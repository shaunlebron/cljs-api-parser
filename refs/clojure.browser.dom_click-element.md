## <img width="48px" valign="middle" src="http://i.imgur.com/Hi20huC.png"> clojure.browser.dom/click-element

 <table border="1">
<tr>
<td>function</td>
<td><a href="https://github.com/cljsinfo/api-refs/tree/0.0-927"><img valign="middle" alt="[+] 0.0-927" src="https://img.shields.io/badge/+-0.0--927-lightgrey.svg"></a> </td>
</tr>
</table>

 <samp>
(__click-element__ e)<br>
</samp>

```
(no docstring)
```

---

 <pre>
clojurescript @ r1424
└── src
    └── cljs
        └── clojure
            └── browser
                └── <ins>[dom.cljs:147-149](https://github.com/clojure/clojurescript/blob/r1424/src/cljs/clojure/browser/dom.cljs#L147-L149)</ins>
</pre>

```clj
(defn click-element
  [e]
  (.click (ensure-element e) ()))
```


---

```clj
{:full-name "clojure.browser.dom/click-element",
 :ns "clojure.browser.dom",
 :name "click-element",
 :type "function",
 :signature ["[e]"],
 :source {:code "(defn click-element\n  [e]\n  (.click (ensure-element e) ()))",
          :filename "clojurescript/src/cljs/clojure/browser/dom.cljs",
          :lines [147 149],
          :link "https://github.com/clojure/clojurescript/blob/r1424/src/cljs/clojure/browser/dom.cljs#L147-L149"},
 :full-name-encode "clojure.browser.dom_click-element",
 :history [["+" "0.0-927"]]}

```