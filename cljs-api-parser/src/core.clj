(ns core
  (:refer-clojure :exclude [replace])
  (:require
    [clojure.java.io :as io]
    [clojure.java.shell :refer [sh]]
    [clojure.tools.reader :as reader]
    [clojure.tools.reader.reader-types :as readers]
    [clojure.string :refer [split-lines join replace trim]]
    [clojure.core.match :refer [match]]
    [cljs.tagged-literals :refer [*cljs-data-readers*]]
    [me.raynes.fs :refer [mkdir]]))

;; location of the documents generated by this program
(def cljsdoc-dir "../../docs-generated")

;; location of the clojure & clojurescript repos to parse
(def repo-dir "repos")

;; SHA1 hashes of the checked out commits of the language repos
(def repo-version
  (atom {"clojure" nil
         "clojurescript" nil
         "core.async" nil}))

;; functions marked as macros
(def ^:dynamic *fn-macros* [])

;; Table of namespaces that we will parse
(def cljs-ns-paths
  ; NS                        REPO             FILE               FULL PATH
  {"cljs.core"              {"clojurescript" {"core.cljs"        "src/cljs/cljs/core.cljs"
                                              "core.clj"         "src/clj/cljs/core.clj"}
                             "clojure"       {"core.clj"         "src/clj/clojure/core.clj"
                                              "core_deftype.clj" "src/clj/clojure/core_deftype.clj"
                                              "core_print.clj"   "src/clj/clojure/core_print.clj"
                                              "core_proxy.clj"   "src/clj/clojure/core_proxy.clj"}}
   "cljs.reader"            {"clojurescript" {"reader.cljs"      "src/cljs/cljs/reader.cljs"}}
   "clojure.set"            {"clojurescript" {"set.cljs"         "src/cljs/clojure/set.cljs"}}
   "clojure.string"         {"clojurescript" {"string.cljs"      "src/cljs/clojure/string.cljs"}}
   "clojure.walk"           {"clojurescript" {"walk.cljs"        "src/cljs/clojure/walk.cljs"}}
   "clojure.zip"            {"clojurescript" {"zip.cljs"         "src/cljs/clojure/zip.cljs"}}
   "clojure.data"           {"clojurescript" {"data.cljs"        "src/cljs/clojure/data.cljs"}}
   "cljs.core.async"        {"core.async"    {"async.cljs"       "src/main/clojure/cljs/core/async.cljs"}}
   "cljs.core.async.macros" {"core.async"    {"macros.clj"       "src/main/clojure/cljs/core/async/macros.clj"}}})

;;------------------------------------------------------------
;; Form Reading
;;------------------------------------------------------------

(defn read-forms
  [r]
  (loop [forms (transient [])]
    (if-let [f (try (binding [reader/*data-readers* *cljs-data-readers*]
                      (reader/read r))
                    (catch Exception e
                      (when-not (= (.getMessage e) "EOF") (throw e))))]
      (recur (conj! forms f))
      (persistent! forms))))

(defn read-forms-from-file
  [path]
  (let [is (io/input-stream path)
        r1 (readers/input-stream-push-back-reader is)
        r  (readers/source-logging-push-back-reader r1 1 path)]
    (read-forms r)))

(defn read-forms-from-str
  [s]
  (let [r (readers/string-push-back-reader s)]
    (read-forms r)))

;;------------------------------------------------------------
;; Repos
;;------------------------------------------------------------

(defn get-repo-version
  [repo]
  (trim (:out (sh "git" "describe" "--tags" :dir (str repo-dir "/" repo)))))

(defn get-github-file-link
  [repo path [start-line end-line]]
  (let [version (get @repo-version repo)
        strip-path (subs path (inc (count repo)))]
    (str "https://github.com/clojure/" repo "/blob/" version "/" strip-path
         "#L" start-line "-L" end-line)))

;;------------------------------------------------------------
;; Repo Helpers
;;------------------------------------------------------------

(defn get-repo-path
  "Get path to the given repo file"
  [ns- repo file]
  (let [path (get-in cljs-ns-paths [ns- repo file])]
    (str repo-dir "/" repo "/" path)))

(defn get-forms
  "Get forms from the given repo file"
  [ns- repo file]
  (read-forms-from-file (get-repo-path ns- repo file)))

;;------------------------------------------------------------
;; Docstring Helpers
;;------------------------------------------------------------

(defn get-docstring-indent
  [docstring]
  (let [lines (split-lines docstring)]
    (if (> (count lines) 1)
      (let [[first-line & indented-lines] lines
            get-indent-length #(count (re-find #"^ *" %))
            has-content? #(pos? (count (trim %)))]
        (->> indented-lines
             (filter has-content?)
             (map get-indent-length)
             (apply min 3)))
      0)))

(defn fix-docstring
  "Remove indentation from docstring."
  [docstring]
  (when (string? docstring)
    (let [indent-length (get-docstring-indent docstring)]
      (if (zero? indent-length)
        docstring
        (let [[first-line & indented-lines] (split-lines docstring)
              indent (re-pattern (str "^ {" indent-length "}"))
              remove-indent #(replace % indent "")]
          (->> indented-lines
               (map remove-indent)
               (cons first-line)
               (join "\n")))))))

(defn try-remove-docs
  "Try to remove docstring/attr-map from source if they are on their expected lines."
  [source {:keys [start-line end-line forms] :as expected-docs}]
  (if (nil? expected-docs)
    source
    (let [i-lines (map-indexed vector (split-lines source))
          to-str #(join "\n" (map second %))
          doc-line? #(<= start-line (first %) end-line)
          doc-str (to-str (filter doc-line? i-lines))
          actual-forms (read-forms-from-str doc-str)]
      (if (= actual-forms forms)
        (to-str (remove doc-line? i-lines))
        (do
          (binding [*out* *err*]
            (println "=====================================")
            (println "Warning: couldn't remove docstring:")
            (println "expected:" (pr-str forms))
            (println "actual:" (pr-str actual-forms))
            (println "source:" (pr-str source))
            (println "====================================="))
          source)))))

(defn try-locate-docs
  "Try to guess which lines the given docs are on (for defn/defmacro)."
  [{:keys [whole head doc sig-body] :as forms}]
  (when (seq doc)
    (let [get-line #(:line (meta %))
          first-line (get-line whole)
          before-line (or (get-line (second head))
                          (get-line (first head)))
          after-line (get-line (first sig-body))]
      (when (< before-line after-line)
        {:start-line (-> before-line inc (- first-line))
         :end-line (-> after-line dec (- first-line))
         :forms doc}))))

;;------------------------------------------------------------
;; Form Parsing
;;------------------------------------------------------------

(defn get-fn-macro
  "looks for a call of the form:
  (. (var %) (setMacro))"
  [form]
  (let [to-vec #(if (seq? %) (vec %) %)]
    (match (to-vec (map to-vec form))
      ['. ['var name-] ['setMacro]] name-
      :else nil)))

(defn get-fn-macros
  [forms]
  (set (keep get-fn-macro forms)))

(defn parse-defn-or-macro
  [form]
  (let [fn-or-macro ({'defn "function" 'defmacro "macro"} (first form))
        args (drop 2 form)
        docstring (let [ds (first args)]
                    (when (string? ds)
                      ds))
        args (if docstring (rest args) args)
        attr-map (let [m (first args)]
                   (when (map? m) m))
        args (if attr-map (rest args) args)
        private? (:private attr-map)
        doc-forms (cond-> []
                    docstring (conj docstring)
                    attr-map (conj attr-map))
        signatures (if (vector? (first args))
                     (take 1 args)
                     (map first args))
        expected-docs (try-locate-docs
                        {:whole form
                         :head (take 2 form)
                         :doc doc-forms
                         :sig-body args})]
    (when-not private?
      {:expected-docs expected-docs
       :docstring (fix-docstring docstring)
       :signatures signatures
       :fn-or-macro fn-or-macro})))

(defn parse-def-fn
  [form]
  (let [name- (second form)
        m (meta name-)
        docstring (fix-docstring (:doc m))
        signatures (when-let [arglists (:arglists m)]
                     (when (= 'quote (first arglists))
                       (second arglists)))]
    {:docstring docstring
     :signatures signatures
     :fn-or-macro "function"}))

(defmulti parse-form*
  (fn [form]
    (cond
      (and (= 'defn (first form))
           (not (:private (meta (second form)))))
      "defn"

      (and (= 'defmacro (first form))
           (not (:private (meta (second form)))))
      "defmacro"

      (and (= 'def (first form))
           (list? (nth form 2 nil))
           (= 'fn (first (nth form 2 nil)))
           (not (:private (meta (second form)))))
      "def fn"

      :else nil)))

(defmethod parse-form* "def fn"
  [form]
  (parse-def-fn form))

(defmethod parse-form* "defn"
  [form]
  (parse-defn-or-macro form))

(defmethod parse-form* "defmacro"
  [form]
  (parse-defn-or-macro form))

(defmethod parse-form* nil
  [form]
  nil)

(defn parse-common
  [form ns- repo]
  (let [name- (second form)
        name-meta (meta name-)
        return-type (:tag name-meta)
        m (meta form)
        lines [(:line m) (:end-line m)]
        num-lines (inc (- (:end-line m) (:line m)))
        source (join "\n" (take-last num-lines (split-lines (:source m))))
        filename (subs (:file m) (inc (count repo-dir)))
        github-link (get-github-file-link repo filename lines)
        manual-macro? (or (*fn-macros* name-)
                          (:macro name-meta))]
    (merge
      {:ns ns-
       :name name-
       :return-type return-type
       :full-name (str ns- "/" name-)
       :source source
       :filename filename
       :lines lines
       :github-link github-link}

      (when manual-macro?
        {:fn-or-macro "macro"}))))

(defn parse-form
  [form ns- repo]
  (when-let [specific (parse-form* form)]
    (let [common (parse-common form ns- repo)
          merged (merge specific common)
          final (update-in merged [:source] try-remove-docs (:expected-docs specific))]
      final)))

(defn parse-api
  "Parse the functions and macros from the given repo file"
  [ns- repo file]
  (let [forms (get-forms ns- repo file)]
    (binding [*fn-macros* (get-fn-macros forms)]
      (doall (keep #(parse-form % ns- repo) forms)))))

(defn get-imported-macro-api
  [forms macro-api]
  (let [get-imports #(match % (['import-macros 'clojure.core x] :seq) x :else nil)
        macro-names (->> forms (keep get-imports) first set)]
    (filter #(macro-names (:name %)) macro-api)))

(defn get-non-excluded-macro-api
  [forms macro-api]
  (let [ns-form (first (filter #(= 'ns (first %)) forms))
        get-excludes #(match % ([:refer-clojure :exclude x] :seq) x :else nil)
        macro-names (->> ns-form (drop 2) (keep get-excludes) first set)]
    (remove #(macro-names (:name %)) macro-api)))

;;------------------------------------------------------------
;; Namespace API parsing
;;------------------------------------------------------------

(defmulti parse-ns-api (fn [ns-] ns-))

(defn parse-extra-macros-from-clj
  []
  (let [clj-api (concat (parse-api "cljs.core" "clojure" "core.clj")
                        (parse-api "cljs.core" "clojure" "core_deftype.clj")
                        (parse-api "cljs.core" "clojure" "core_print.clj")
                        (parse-api "cljs.core" "clojure" "core_proxy.clj"))
        clj-api (filter #(= "macro" (:fn-or-macro %)) clj-api)
        cljs-forms   (get-forms "cljs.core" "clojurescript" "core.clj")
        imports      (get-imported-macro-api     cljs-forms clj-api)
        non-excludes (get-non-excluded-macro-api cljs-forms clj-api)]
    (println "   " (count imports) "imported clojure.core macros")
    (println "   " (count non-excludes) "non-excluded clojure.core macros")
    (concat imports non-excludes)))

(defmethod parse-ns-api "cljs.core" [ns-]
  (let [clj-api  (parse-api ns- "clojurescript" "core.clj")
        cljs-api (parse-api ns- "clojurescript" "core.cljs")
        extra-macro-api (parse-extra-macros-from-clj)]
    (concat extra-macro-api clj-api cljs-api)))

(defmethod parse-ns-api "cljs.reader" [ns-]
  (parse-api ns- "clojurescript" "reader.cljs"))

(defmethod parse-ns-api "clojure.set" [ns-]
  (parse-api ns- "clojurescript" "set.cljs"))

(defmethod parse-ns-api "clojure.string" [ns-]
  (parse-api ns- "clojurescript" "string.cljs"))

(defmethod parse-ns-api "clojure.walk" [ns-]
  (parse-api ns- "clojurescript" "walk.cljs"))

(defmethod parse-ns-api "clojure.zip" [ns-]
  (parse-api ns- "clojurescript" "zip.cljs"))

(defmethod parse-ns-api "clojure.data" [ns-]
  (parse-api ns- "clojurescript" "data.cljs"))

(defmethod parse-ns-api "cljs.core.async" [ns-]
  (parse-api ns- "core.async" "async.cljs"))

(defmethod parse-ns-api "cljs.core.async.macros" [ns-]
  (parse-api ns- "core.async" "macros.clj"))

;;------------------------------------------------------------
;; Doc file writing
;;------------------------------------------------------------

(defn symbol->filename
  [s]
  (-> (name s)
      (replace "." "DOT")
      (replace ">" "GT")
      (replace "<" "LT")
      (replace "!" "BANG")
      (replace "?" "QMARK")
      (replace "*" "STAR")
      (replace "+" "PLUS")
      (replace "/" "SLASH")))

(defn item-filename
  [item]
  (str cljsdoc-dir "/" (:ns item) "_" (symbol->filename (:name item))))

(defn cljsdoc-section
  [title content]
  (when content
    (str "===== " title "\n" content "\n")))

(defn make-cljsdoc
  [item]
  (join "\n"
    (keep identity
      [(cljsdoc-section "Name" (:full-name item))
       (cljsdoc-section "Type" (:fn-or-macro item))
       (cljsdoc-section "Return" (:return-type item))
       (cljsdoc-section "Docstring" (:docstring item))
       (cljsdoc-section "Signature" (join "\n" (:signatures item)))
       (cljsdoc-section "Filename" (:filename item))
       (cljsdoc-section "Source" (:source item))
       (cljsdoc-section "Github" (:github-link item))])))

(defn dump-doc-file!
  [item]
  (let [filename (item-filename item)
        cljsdoc-content (make-cljsdoc item)]
    (spit (str filename ".cljsdoc") cljsdoc-content)))

(defn dump-api-docs!
  [api]
  (doseq [item api]
    (dump-doc-file! item)))

;;------------------------------------------------------------
;; Program Entry
;;------------------------------------------------------------

(defn -main
  []
  (try

    ;; Retrieve the SHA1 hashes for the checked out repos (for github links)
    (println "\nUsing repo versions:")
    (doseq [repo (keys @repo-version)]
      (let [v (get-repo-version repo)]
        (swap! repo-version assoc repo v)
        (println " " repo ":" v)))

    ;; HACK: We need to create this so 'tools.reader' doesn't crash on `::ana/numeric`
    ;; which is used by cljs.core. (the ana namespace has to exist)
    (create-ns 'ana)

    ;; create the output directory for the docs
    (mkdir cljsdoc-dir)

    (println "\nWriting docs to" cljsdoc-dir)

    ;; Build the docs.
    (doseq [ns- (keys cljs-ns-paths)]
      (println " " ns-)
      (dump-api-docs! (parse-ns-api ns-)))

    (println "\nDone.")

    ;; have to do this because `sh` leaves futures hanging,
    ;; preventing exit, so we must do it manually.
    (finally (System/exit 0))))

