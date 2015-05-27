## <img width="48px" valign="middle" src="http://i.imgur.com/Hi20huC.png"> special/deftype\*

 <table border="1">
<tr>
<td>special form</td>
<td><a href="https://github.com/cljsinfo/api-refs/tree/0.0-927"><img valign="middle" alt="[+] 0.0-927" src="https://img.shields.io/badge/+-0.0--927-lightgrey.svg"></a> </td>
</tr>
</table>

 <samp>
</samp>

```
(no docstring)
```

---

 <pre>
clojurescript @ r1424
└── src
    └── clj
        └── cljs
            └── <ins>[analyzer.clj:657-671](https://github.com/clojure/clojurescript/blob/r1424/src/clj/cljs/analyzer.clj#L657-L671)</ins>
</pre>

```clj
(defmethod parse 'deftype*
  [_ env [_ tsym fields pmasks :as form] _]
  (let [t (:name (resolve-var (dissoc env :locals) tsym))]
    (swap! namespaces update-in [(-> env :ns :name) :defs tsym]
           (fn [m]
             (let [m (assoc (or m {})
                       :name t
                       :type true
                       :num-fields (count fields))]
               (merge m
                 {:protocols (-> tsym meta :protocols)}     
                 (when-let [line (:line env)]
                   {:file *cljs-file*
                    :line line})))))
    {:env env :op :deftype* :form form :t t :fields fields :pmasks pmasks}))
```


---

```clj
{:full-name "special/deftype*",
 :ns "special",
 :name "deftype*",
 :type "special form",
 :source {:code "(defmethod parse 'deftype*\n  [_ env [_ tsym fields pmasks :as form] _]\n  (let [t (:name (resolve-var (dissoc env :locals) tsym))]\n    (swap! namespaces update-in [(-> env :ns :name) :defs tsym]\n           (fn [m]\n             (let [m (assoc (or m {})\n                       :name t\n                       :type true\n                       :num-fields (count fields))]\n               (merge m\n                 {:protocols (-> tsym meta :protocols)}     \n                 (when-let [line (:line env)]\n                   {:file *cljs-file*\n                    :line line})))))\n    {:env env :op :deftype* :form form :t t :fields fields :pmasks pmasks}))",
          :filename "clojurescript/src/clj/cljs/analyzer.clj",
          :lines [657 671],
          :link "https://github.com/clojure/clojurescript/blob/r1424/src/clj/cljs/analyzer.clj#L657-L671"},
 :full-name-encode "special_deftype_STAR_",
 :history [["+" "0.0-927"]]}

```