(ns cljs-api-gen.catalog
  (:require
    [fipp.edn :refer [pprint]]
    [clojure.edn :as edn]
    [clansi.core :refer [style]]
    [clojure.string :refer [join]]
    [clojure.java.shell :refer [sh]]
    [me.raynes.fs :refer [mkdir
                          exists?
                          file?
                          delete-dir
                          list-dir
                          base-name
                          copy
                          copy-dir]]
    [cljs-api-gen.encode :refer [decode-fullname]]
    [cljs-api-gen.cljsdoc :refer [build-doc
                                  build-cljsdoc!
                                  create-cljsdoc-stubs!]]
    [cljs-api-gen.config :refer [*output-dir*
                                 cache-dir
                                 edn-parsed-file
                                 edn-cljsdoc-file]]
    [cljs-api-gen.parse :refer [parse-all]]
    [cljs-api-gen.repo-cljs :refer [get-cljs-tags-to-parse
                                    cljs-version->tag
                                    published-cljs-tags
                                    with-checkout!
                                    cljs-tag->version
                                    *cljs-tag*
                                    *cljs-date*
                                    *clj-tag*
                                    *cljs-version*
                                    *clj-version*]]
    [cljs-api-gen.result :refer [add-cljsdoc
                                 get-result
                                 add-cljsdoc-to-result]]
    [cljs-api-gen.write :refer [dump-result!
                                dump-site-pages!
                                dump-var-file!] :as write]
    [clojure-watch.core :refer [start-watch]]
    ))

;;----------------------------------------------------------------------
;; Catalog Repo Operations
;;----------------------------------------------------------------------

(defn git
  [& args]
  (apply sh "git" (concat args [:dir *output-dir*])))

(defn catalog-tag->cljs
  [v]
  (let [m (re-find #"-\d+" v)
        number (subs m 1)]
    (str "r" number)))

(defn catalog-tag []
  (:out (git "describe" "--tags")))

(defn catalog-init! []
  (delete-dir (str *output-dir* "/.git"))
  (git "init"))

(defn catalog-add!
  [f]
  (git "add" f))

(defn catalog-commit! []
  (let [msg (str *cljs-version* "\n"
                 "\n"
                 "- auto-generated by:\n"
                 "  https://github.com/cljsinfo/cljs-api-docs\n"
                 "\n"
                 "- parsed from:\n"
                 "  ClojureScript " *cljs-version* "\n"
                 "  Clojure " *clj-version* "\n")]
    (git "commit" "-m" msg)
    (git "tag" *cljs-version*)))

;;----------------------------------------------------------------------
;; Catalog Creation
;;----------------------------------------------------------------------

(defn print-summary*
  [parsed]
  (let [ns-groups (group-by :ns parsed)
        pairs (sort-by first ns-groups)]
    (doseq [[ns- symbols] pairs]
      (printf "    %-24s %4s = %s\n"
        ns-
        (count symbols)
        (let [type-groups (group-by :type symbols)
              pairs (sort-by first type-groups)]
          (join " + "
            (for [[type- symbols] pairs]
              (let [total (count symbols)]
                (str total " " (cond-> type- (> total 1) (str "s")))))))))))

(defn print-summary
  [parsed]
  (println " Syntax API:")
  (print-summary* (:syntax parsed))
  (println " Library API:")
  (print-summary* (:library parsed))
  (println " Compiler API:")
  (print-summary* (:compiler parsed)))

(defn create-catalog!
  [{:as options
    :keys [version
           catalog?
           watch?
           gen-site?
           skip-pages?
           skip-parse?]
    :or {version :latest
         catalog? false
         skip-pages? true
         skip-parse? true}}]

  ;; create output directory
  (when-not (exists? *output-dir*)
    (mkdir *output-dir*))

  (println (style "\nCreating api catalog...\n" :cyan))

  (let [cache (str *output-dir* "/" cache-dir)

        ;; This holds the "result" data for the most recently parsed cljs version.
        ;; Sometimes this is cached, so there is some ceremony here to prevent pulling
        ;; the value from its cache until its actually needed.  We use an expression
        ;; wrapped in a `delay` to do this.  Since not every value is cached, we just
        ;; use the `get-prev-result` to not worry about it.
        prev-result (atom nil)
        get-prev-result #(if (delay? @prev-result)
                           @@prev-result
                           @prev-result)

        ;; normalize the given version to a tag if needed
        version (or (and (string? version)
                         (cljs-version->tag version))
                    version)

        tags (case version
               :latest @published-cljs-tags
               :master (concat @published-cljs-tags ["master"])
               (if-not ((set @published-cljs-tags) version)
                 (do
                   (println (style "Unrecognized version tag" :red) version)
                   (System/exit 1))
                 (concat (take-while (partial not= version) @published-cljs-tags) [version])))

        skipped-previous? (atom false)
        last-tag (last tags)

        full-result (atom nil)]

    ;; make cache directory
    (when-not (exists? cache)
      (mkdir cache))

    (println "Outputting to " (style *output-dir* :cyan))
    (println "   with cache at " (style cache :cyan))

    ;; parse symbol history
    (println (style "\nStarting first pass (parsing symbol history)...\n" :magenta))
    (reset! skipped-previous? false)
    (doseq [tag tags]

      ;; check if skip-parse? and if this tag's edn-parsed-file already exists
      (let [out-folder (str cache "/" tag)
            parsed-file (str out-folder "/" edn-parsed-file)
            skip? (and skip-parse?           ;; do we want to skip?
                       (exists? parsed-file) ;; can we skip?
                       )]

        ;; make output folder for this tag
        (when-not (exists? out-folder)
          (mkdir out-folder))

        (if skip?

          (do
            (when-not @skipped-previous?
              (println (style "\nUsing cache instead of parsing:" :yellow)))
            (print (str " " tag))
            (reset! skipped-previous? true)
            (reset! prev-result (delay (edn/read-string (slurp parsed-file)))))

          ;; parse
          (with-checkout! tag
            (reset! skipped-previous? false)

            (println "\n\n=========================================================")
            (println "\nChecked out ClojureScript " (style *cljs-tag* :yellow))
            (println "with Clojure:" (style *clj-tag* :yellow))
            (println "published on" (style *cljs-date* :yellow))

            (println "\nParsing...")
            (let [parsed (parse-all)]
              (print-summary parsed)

              (println "\nWriting parsed data to" (style parsed-file :cyan))
              (let [result (get-result parsed (get-prev-result))]
                (spit parsed-file (with-out-str (pprint result)))
                (reset! prev-result result)))

            (println "\nDone.")))))

    (println)
    (let [result (get-prev-result)]

      ;; create cljsdoc stubs for symbols that don't have them
      ;; (allowing easier PRs for those wanting to populate them)
      (create-cljsdoc-stubs! (-> result :symbols keys set))
      (create-cljsdoc-stubs! (-> result :namespaces keys set))

      ;; compile cljsdoc files (manual docs)
      (let [num-skipped (build-cljsdoc! result)]
        (when-not (zero? num-skipped)
          (System/exit 1))))

    ;; create pages
    (println (style "\nStarting second pass (merge manual docs and create pages)...\n" :magenta))
    (reset! skipped-previous? false)
    (doseq [tag tags]
      (let [skip? (and skip-pages? (not= tag last-tag))
            out-folder (str cache "/" tag)]
        (if skip?
          (do
            (when-not @skipped-previous?
              (println (style "\nSkipping page creation for:" :yellow)))
            (print (str " " tag))
            (reset! skipped-previous? true))
          (do
            (reset! skipped-previous? false)
            (println "\n\nCreating pages for " (style tag :yellow))
            (let [parsed-file (str out-folder "/" edn-parsed-file)
                  parsed (edn/read-string (slurp parsed-file))
                  result (add-cljsdoc-to-result parsed)]
              (reset! full-result result)
              (binding [*output-dir* out-folder]
                (dump-result! result)
                (when gen-site?
                  (dump-site-pages! result))
                ))))))

    ;; third pass
    (println (style "\nStarting final pass (finalizing output directory)...\n" :magenta))
    (let [dont-copy? #{edn-parsed-file}
          should-copy? (complement dont-copy?)
          files-to-copy (fn [tag]
                          (->> (list-dir (str cache "/" tag))
                               (filter #(should-copy? (base-name %)))))
          copy-to-root! (fn [tag]
                          (doseq [f (files-to-copy tag)]
                            (let [filename (base-name f)
                                  new-loc (str *output-dir* "/" filename)]
                              (if (file? f)
                                (copy f new-loc)
                                (do
                                  (delete-dir new-loc)
                                  (copy-dir f new-loc))))))]

      (if catalog?

        (do
          (println "\nCreating catalog repo...")
          (catalog-init!)
          (doseq [tag (if (= "master" last-tag)
                        (butlast tags) ;; don't commit the master tag to the catalog
                        tags)]

            ;; FIXME: We shouldn't be checking out the repos here, but this
            ;; wrapper gives us the version bindings, which we haven't separated
            ;; into its own macro yet.
            (with-checkout! tag
              (println "\nCommitting docs at tag" tag "...")
              (copy-to-root! tag)
              (doseq [f (files-to-copy tag)]
                (catalog-add! (base-name f)))
              (catalog-commit!))))

        (copy-to-root! last-tag)))

    (println (style " Success! " :bg-green))

    (when (and watch? (not catalog?))

      (start-watch
        [{:path "cljsdoc"
          :event-types [:modify]
          :bootstrap (fn [path] (println (style "\nStarting to watch for cljsdoc changes..." :yellow)))
          :callback
          (fn [event filename]
            (when-not (.endsWith filename ".swp")
              (println "\nre-rendering" filename "changes...")
              (let [full-name (decode-fullname (base-name filename true))

                    ;; get this symbol's parsed information (before cljsdoc was merged in)
                    item (get-in (get-prev-result) [:symbols full-name])]

                ;; compile cljsdoc
                (when-let [cljsdoc (build-doc filename)]

                  ;; add cljsdoc to the parsed info
                  (let [new-item (add-cljsdoc item last-tag cljsdoc)]

                    ;; update symbol in the full-result map
                    (swap! full-result assoc-in [:symbols full-name] new-item)

                    ;; dump the new ref file
                    (binding [write/*result* @full-result]
                      (dump-var-file! new-item)))

                  (println "Done.")))))
          }])

      )))

