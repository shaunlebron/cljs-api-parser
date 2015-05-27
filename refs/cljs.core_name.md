## <img width="48px" valign="middle" src="http://i.imgur.com/Hi20huC.png"> cljs.core/name

 <table border="1">
<tr>
<td>function</td>
<td><a href="https://github.com/cljsinfo/api-refs/tree/0.0-927"><img valign="middle" alt="[+] 0.0-927" src="https://img.shields.io/badge/+-0.0--927-lightgrey.svg"></a> </td>
<td>
[<img height="24px" valign="middle" src="http://i.imgur.com/1GjPKvB.png"> <samp>clojure.core/name</samp>](http://clojure.github.io/clojure/branch-master/clojure.core-api.html#clojure.core/name)
</td>
</tr>
</table>

 <samp>
(__name__ x)<br>
</samp>

```
Returns the name String of a string, symbol or keyword.
```

---

 <pre>
clojurescript @ r1424
└── src
    └── cljs
        └── cljs
            └── <ins>[core.cljs:5739-5749](https://github.com/clojure/clojurescript/blob/r1424/src/cljs/cljs/core.cljs#L5739-L5749)</ins>
</pre>

```clj
(defn name
  [x]
  (cond
    (string? x) x
    (or (keyword? x) (symbol? x))
      (let [i (.lastIndexOf x "/")]
        (if (< i 0)
          (subs x 2)
          (subs x (inc i))))
    :else (throw (js/Error. (str "Doesn't support name: " x)))))
```


---

```clj
{:ns "cljs.core",
 :name "name",
 :signature ["[x]"],
 :history [["+" "0.0-927"]],
 :type "function",
 :full-name-encode "cljs.core_name",
 :source {:code "(defn name\n  [x]\n  (cond\n    (string? x) x\n    (or (keyword? x) (symbol? x))\n      (let [i (.lastIndexOf x \"/\")]\n        (if (< i 0)\n          (subs x 2)\n          (subs x (inc i))))\n    :else (throw (js/Error. (str \"Doesn't support name: \" x)))))",
          :filename "clojurescript/src/cljs/cljs/core.cljs",
          :lines [5739 5749],
          :link "https://github.com/clojure/clojurescript/blob/r1424/src/cljs/cljs/core.cljs#L5739-L5749"},
 :full-name "cljs.core/name",
 :clj-symbol "clojure.core/name",
 :docstring "Returns the name String of a string, symbol or keyword."}

```