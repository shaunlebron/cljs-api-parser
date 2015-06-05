(ns cljs-api-gen.clojure-api
  (:require
    [clojure.set :refer [difference]]
    [cljs-api-gen.repo-cljs :refer [*clj-tag* ls-files]]
    [me.raynes.fs :refer [base-name]]
    ))

;;--------------------------------------------------------------------------------
;; Official Clojure API
;;--------------------------------------------------------------------------------

(defn clj-tag->api-key [tag]
  (second (re-find #"clojure-(\d\.\d)" tag)))

(def versions ["1.3" "1.4" "1.5" "1.6" "1.7"])
(def api-symbols (atom {}))

(defn version-api-url [v]
  (str "https://raw.githubusercontent.com/clojure/clojure/gh-pages/index-v" v ".clj"))

(defn get-version-apis! []
  (doseq [v versions]
    (println "Retrieving clojure" v "API...")
    (let [data (read-string (slurp (version-api-url v)))
          symbols (->> (:vars data)
                       (map #(str (:namespace %) "/" (:name %)))
                       set)]
      (swap! api-symbols assoc v symbols))))

;;--------------------------------------------------------------------------------
;; Clojure's Types and Protocols
;;
;;  - `clojure.lang` namespace (non-public)
;;  - `clojure.lang.PersistentQueue/EMPTY` has to be used to create queues,
;;    so might as well bring all the types in and relate them to the cljs
;;--------------------------------------------------------------------------------

(def lang-symbols->parent
  "These clojure.lang symbols don't have their own file.
  They belong in the files named after their respective mapped values."

  {"ArrayNode"                "PersistentHashMap"
   "BitmapIndexedNode"        "PersistentHashMap"
   "EmptyList"                "PersistentList"

   "HashCollisionNode"        "PersistentHashmap"
   "KeySeq"                   "APersistentMap"
   "RSeq"                     "APersistentVector"
   "ValSeq"                   "APersistentMap"
   "NodeSeq"                  "PersistentHashMap"
   "ChunkedSeq"               "PersistentVector"

   "TransientArrayMap"        "PersistentArrayMap"
   "TransientHashMap"         "PersistentHashMap"
   "TransientHashSet"         "PersistentHashSet"
   "TransientVector"          "PersistentVector"

   "BitmapIndexedNode.EMPTY"  "PersistentHashMap"
   "PersistentList.EMPTY"     "PersistentList"
   "PersistentArrayMap.EMPTY" "PersistentArrayMap"
   "PersistentHashMap.EMPTY"  "PersistentHashMap"
   "PersistentHashSet.EMPTY"  "PersistentHashSet"
   "PersistentQueue.EMPTY"    "PersistentQueue"
   "PersistentTreeMap.EMPTY"  "PersistentTreeMap"
   "PersistentTreeSet.EMPTY"  "PersistentTreeSet"
   "PersistentVector.EMPTY"   "PersistentVector"
   })

(def lang-symbols (atom {}))
(def lang-path "src/jvm/clojure/lang/")
(defn get-lang-symbols! [tag]
  (if-let [symbols (@lang-symbols tag)]
    symbols
    (let [ns- "clojure.lang"
          symbols (->> (ls-files "clojure" tag lang-path)
                       (filter #(.endsWith % ".java"))
                       (map #(str ns- "/" (base-name % true)))
                       (concat (map #(str ns- "/" %) (keys lang-symbols->parent)))
                       set)]
      (swap! lang-symbols assoc tag symbols)
      symbols)))

;;--------------------------------------------------------------------------------
;; Clojure's Syntax
;;
;;   - tagged literals (from `clojure.core/default-data-readers` >= 1.4)
;;   - syntax (from `clojure.lang/LispReader`)
;;--------------------------------------------------------------------------------

(def clj-base-tagged-lits
  "hard-coded base set of tagged literals in Clojure. from:
  https://github.com/clojure/clojure/blob/028af0e0b271aa558ea44780e5d951f4932c7842/src/clj/clojure/core.clj#L6947"

  #{"#uuid"
    "#inst"})

(defn clj-tagged-lits
  []
  ;; NOTE: When new tagged literals are added in future versions of Clojure,
  ;;       add logic here to conj them on to `clj-base-tagged-lits`
  ;;       rather than trying to parse them from LispReader.java.
  clj-base-tagged-lits)

(def clj-base-syntax
  "hard-coded base set of syntax readers in Clojure. from:
  https://github.com/clojure/clojure/blob/clojure-1.7.0-RC1/src/jvm/clojure/lang/LispReader.java#L87-L118"

  [{:form "\"" :desc "string"}
   {:form ":" :desc "keyword"}
   {:form ";" :desc "comment"}
   {:form "'" :desc "quote"}
   {:form "@" :desc "deref"}
   {:form "^" :desc "meta"}
   {:form "`" :desc "syntax-quote"}
   {:form "~" :desc "unquote"}
   {:form "()" :desc "list"}
   {:form "[]" :desc "vector"}
   {:form "{}" :desc "map"}
   {:form "\\" :desc "character"}
   {:form "%" :desc "arg"}
   {:form "#'" :desc "var"}
   {:form "#()" :desc "function"}
   {:form "#=" :desc "eval"}
   {:form "#{}" :desc "set"}
   {:form "#\"\"" :desc "regex"}
   {:form "#!" :desc "hashbang"}
   {:form "#_" :desc "ignore"}
   {:desc "number"}
   {:desc "symbol"}
   ])

(defn clj-syntax
  ;; NOTE: When new syntax is added in future versions of Clojure,
  ;;       add logic here to conj them on to `clj-base-tagged-lits`
  ;;       rather than trying to parse them from LispReader.java.
  []
  (case (clj-tag->api-key *clj-tag*)
    ("1.3" "1.4" "1.5" "1.6") clj-base-syntax
    (conj clj-base-syntax {:form "#?" :desc "cond"}) ;; add conditional reader, available >= 1.7
    ))

;;--------------------------------------------------------------------------------
;; ClojureScript -> Clojure name mapping
;;--------------------------------------------------------------------------------

(def cljs-ns->clj
  {"cljs.core"   "clojure.core"
   "cljs.pprint" "clojure.pprint"
   "cljs.test"   "clojure.test"
   "cljs.repl"   "clojure.repl"
   "special"     "clojure.core"
   "specialrepl" "clojure.core"
   })

(def cljs-full-name->clj
  "cljs symbols that map to different clj names."

  {;; library
   "cljs.core/*clojurescript-version*"  "clojure.core/*clojure-version*"
   "cljs.reader/read-string"            "clojure.core/read-string"
   "cljs.reader/read"                   "clojure.core/read"

   ;; compiler
   "cljs.analyzer.api/all-ns"           "clojure.core/all-ns"
   "cljs.analyzer.api/find-ns"          "clojure.core/find-ns"
   "cljs.analyzer.api/ns-interns"       "clojure.core/ns-interns"
   "cljs.analyzer.api/ns-publics"       "clojure.core/ns-publics"
   "cljs.analyzer.api/ns-resolve"       "clojure.core/ns-resolve"
   "cljs.analyzer.api/remove-ns"        "clojure.core/remove-ns"
   "cljs.analyzer.api/resolve"          "clojure.core/resolve"

   ;; protocols
   "cljs.core/IAssociative"             "clojure.lang/Associative"
   "cljs.core/ICounted"                 "clojure.lang/Counted"
   "cljs.core/IIndexed"                 "clojure.lang/Indexed"
   "cljs.core/IList"                    "clojure.lang/IPersistentList"
   "cljs.core/INamed"                   "clojure.lang/Named"
   "cljs.core/IReversible"              "clojure.lang/Reversible"
   "cljs.core/ISeqable"                 "clojure.lang/Seqable"
   "cljs.core/ISequential"              "clojure.lang/Sequential"
   "cljs.core/ISet"                     "clojure.lang/IPersistentSet"
   "cljs.core/ISorted"                  "clojure.lang/Sorted"
   "cljs.core/IStack"                   "clojure.lang/IPersistentStack"
   "cljs.core/IVector"                  "clojure.lang/IPersistentVector"

   ;; types
   "cljs.core/List"                     "clojure.lang/PersistentList"
   "cljs.core/SeqIter"                  "clojure.lang/SeqIterator"

   ;; member attributes
   "cljs.core/List.EMPTY"               "clojure.lang/PersistentList.EMPTY"

   ;; tagged literals
   "syntax/#uuid" "#uuid"
   "syntax/#inst" "#inst"
   })

(defn clj-lookup-name
  [item]
  (or (cljs-full-name->clj (:full-name item))
      (when (and (= "cljs.core" (:ns item))
                 (or (:parent-type item)
                     (#{"type" "protocol"} (:type item))))
        (str "clojure.lang/" (:name item)))
      (when-let [clj-ns (cljs-ns->clj (:ns item))]
        (str clj-ns "/" (:name item)))
      (:full-name item)))

(defn attach-clj-symbol
  [item]
  (let [clj-symbol? (get @api-symbols (clj-tag->api-key *clj-tag*))
        lang-symbol? (get-lang-symbols! *clj-tag*)
        tagged-lit? (clj-tagged-lits)
        lookup-name (clj-lookup-name item)]
    (if (or (lang-symbol? lookup-name)
            (clj-symbol? lookup-name)
            (tagged-lit? lookup-name))
      (assoc item :clj-symbol lookup-name)
      item)))

(defn get-clojure-symbols-not-in-items
  [items]
  (let [clj-symbols (into (get @api-symbols (clj-tag->api-key *clj-tag*))
                          (get @lang-symbols *clj-tag*))
        cljs-symbols (set (map clj-lookup-name items))]
    (difference clj-symbols cljs-symbols)))

