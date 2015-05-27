## <img width="48px" valign="middle" src="http://i.imgur.com/Hi20huC.png"> cljs.core/>=

 <table border="1">
<tr>
<td>function</td>
<td><a href="https://github.com/cljsinfo/api-refs/tree/0.0-927"><img valign="middle" alt="[+] 0.0-927" src="https://img.shields.io/badge/+-0.0--927-lightgrey.svg"></a> </td>
<td>
[<img height="24px" valign="middle" src="http://i.imgur.com/1GjPKvB.png"> <samp>clojure.core/>=</samp>](http://clojure.github.io/clojure/branch-master/clojure.core-api.html#clojure.core/%3E=)
</td>
</tr>
</table>

 <samp>
(__>=__ x)<br>
(__>=__ x y)<br>
(__>=__ x y & more)<br>
</samp>

```
Returns non-nil if nums are in monotonically non-increasing order,
otherwise false.
```

---

 <pre>
clojurescript @ r1424
└── src
    └── cljs
        └── cljs
            └── <ins>[core.cljs:1241-1251](https://github.com/clojure/clojurescript/blob/r1424/src/cljs/cljs/core.cljs#L1241-L1251)</ins>
</pre>

```clj
(defn ^boolean >=
  ([x] true)
  ([x y] (cljs.core/>= x y))
  ([x y & more]
   (if (cljs.core/>= x y)
     (if (next more)
       (recur y (first more) (next more))
       (cljs.core/>= y (first more)))
     false)))
```


---

 <pre>
clojurescript @ r1424
└── src
    └── clj
        └── cljs
            └── <ins>[core.clj:153-156](https://github.com/clojure/clojurescript/blob/r1424/src/clj/cljs/core.clj#L153-L156)</ins>
</pre>

```clj
(defmacro >=
  ([x] true)
  ([x y] (bool-expr (list 'js* "(~{} >= ~{})" x y)))
  ([x y & more] `(and (>= ~x ~y) (>= ~y ~@more))))
```

---

```clj
{:return-type boolean,
 :ns "cljs.core",
 :name ">=",
 :signature ["[x]" "[x y]" "[x y & more]"],
 :shadowed-sources ({:code "(defmacro >=\n  ([x] true)\n  ([x y] (bool-expr (list 'js* \"(~{} >= ~{})\" x y)))\n  ([x y & more] `(and (>= ~x ~y) (>= ~y ~@more))))",
                     :filename "clojurescript/src/clj/cljs/core.clj",
                     :lines [153 156],
                     :link "https://github.com/clojure/clojurescript/blob/r1424/src/clj/cljs/core.clj#L153-L156"}),
 :history [["+" "0.0-927"]],
 :type "function",
 :full-name-encode "cljs.core__GT__EQ_",
 :source {:code "(defn ^boolean >=\n  ([x] true)\n  ([x y] (cljs.core/>= x y))\n  ([x y & more]\n   (if (cljs.core/>= x y)\n     (if (next more)\n       (recur y (first more) (next more))\n       (cljs.core/>= y (first more)))\n     false)))",
          :filename "clojurescript/src/cljs/cljs/core.cljs",
          :lines [1241 1251],
          :link "https://github.com/clojure/clojurescript/blob/r1424/src/cljs/cljs/core.cljs#L1241-L1251"},
 :full-name "cljs.core/>=",
 :clj-symbol "clojure.core/>=",
 :docstring "Returns non-nil if nums are in monotonically non-increasing order,\notherwise false."}

```