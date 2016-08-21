(ns structural-typing.preds
  "A few predefined predicates, but also functions that take expected values
  and generate predicates."
  (:use [structural-typing.clojure.core :exclude [not-empty?]])
  (:require [structural-typing.assist.lifting :as lifting]
            [structural-typing.assist.oopsie :as oopsie]
            [structural-typing.assist.predicate-defining :as pdef]
            [structural-typing.guts.type-descriptions.type-expander :as type-expander]
            [structural-typing.guts.type-descriptions :as type-descriptions]
            [such.readable :as readable])
  (:use structural-typing.assist.special-words)
  (:refer-clojure :exclude [any?]))

(defn member
  "Produce a predicate that's false when applied to a value not a
   member of `coll`. The explainer associated with `member` prints
   `coll`.
     
         user=> (type! :small-primes {:n (member [2 3 5 7])})
         user=> (built-like :small-primes {:n 2000})
         :n should be a member of `[2 3 5 7]`; it is `2000`
         => nil
"
  [coll]
  (pdef/compose-predicate
   (format "(member %s)" (readable/value-string coll))
   #(boolean ((set coll) %))
   (pdef/should-be "%s should be a member of `%s`; it is `%s`" coll)))

(defn exactly
  "A predicate that succeeds exactly when its argument is `=` to `expected`.
   Here's an example of defining a versioned type:

        (type! :V5 {:version (exactly 5)
                    :request ...
                    :response ...
                    ...}

   You can also use `exactly` as a way to check for the presence of a function,
   rather than applying that function as a checker:

        (built-like? even? even?) ; false: the function `even?` is not an even number
        (built-like? (exactly even?) even?) ; true
"
  [expected]
  (pdef/compose-predicate
   (format "(exactly %s)" (readable/value-string expected))
   (partial = expected)
   (pdef/should-be "%s should be exactly `%s`; it is `%s`" expected)))

(defn exactly==
  "This predicate is like [[exactly]] except it uses `==` instead of `=`. That is,
  it checks equality irrespective of type. That is, whereas
  `((exactly 1M) 1)` is `false`, `((exactly== 1M) 1)` is `true`."

  [expected]
  (pdef/compose-predicate
   (format "(exactly== %s)" (readable/value-string expected))
   (partial == expected)
   (pdef/should-be "%s should be `==` to `%s`; it is `%s`" expected)))


(defn matches
  "Produce a predicate that returns true when any part of a
   string matches `regex`. (That is, `re-find` is used instead
   of `re-matches`.)

       user=> (built-like (pred/matches #\"ab+\") \"Look at this: abbb. Cool huh?\")
       \"Look at this: abbb. Cool huh?\"
"
  [regex]
  (pdef/compose-predicate
   (format "(matches %s)" (pr-str regex))
   (fn [actual] (boolean (re-find regex actual)))
   (fn [oopsie]
     (let [actual (:leaf-value oopsie)
           readable-actual (readable/value-string actual)]

       (format "%s should match %s; it is %s"
               (oopsie/friendly-path oopsie)
               (pr-str regex)
               (if (string? actual)
                 readable-actual
                 (str "`" readable-actual "`")))))))



(def not-empty?
  "Provides a more pleasant error explanation than `(complement empty?)`
   or `seq`.

        user=> (built-like pred/not-empty? [])
        Value should be a non-empty collection; it is `[]`
        => nil
"
  (pdef/compose-predicate
   "not-empty?"
   (complement empty?)
   (fn [oopsie]
     (format "%s should be a non-empty collection; it is `%s`"
             (oopsie/friendly-path oopsie)
             (readable/value-string (:leaf-value oopsie))))))

(defn- key-differences [expected-keycoll actual-value]
  (let [actual-set (set (keys actual-value))
        expected-keyset (set expected-keycoll)
        extras (set-difference actual-set expected-keyset)
        missing (set-difference expected-keyset actual-set)]
    (cond (not-empty? extras) [:actual-has-extras extras]
          (not-empty? missing) [:actual-missing-required missing]
          :else [:ok])))

(defn at-most-keys
  "Produce a predicate that's false when applied to a map or record with keys other than
   those given in `coll`. Note: the value may be *missing* keys in `coll`. See [[exactly-keys]].
   
         user=> (type! :X {:v1 string?
                           :v2 integer?}
                          (at-most-keys :v1 :v2))
         user=> (built-like :X {:v1 \"apple\"})
         => {:v1 \"apple\"}
         user=> (built-like :X {:v1 \"apple\" :v2 3})
         => {:v1 \"apple\", :v2 3}
         user=> (built-like :X {:v1 \"apple\" :v2 3, :actual-is-too-big true})
         Value has extra keys: #{:actual-is-too-big}; it is {:v1 \"apple\", ...
         => nil
   
   Note: this predicate works only with keys, not paths.

   Note also: all that matters is the presence of keys, not their values."
  [& coll]
  (letfn []
    (->> (fn [actual]
           (let [[status _] (key-differences coll actual)]
             (not= status :actual-has-extras)))
         (show-as (cl-format nil "(at-most-keys ~{~A~^ ~})" coll))
         (explain-with (fn [oopsie]
                         (format "%s has extra keys: %s; it is %s"
                                 (oopsie/friendly-path oopsie)
                                 (second (key-differences coll (:leaf-value oopsie)))
                                 (:leaf-value oopsie)))))))

(defn exactly-keys
  "Produce a predicate that's false when applied to a map or record
   with keys at all different than those given in `coll`. See
   [[at-most-keys]] for a variant that allows the value to be missing
   some of the `coll` keys.
   
        user=> (type! :X {:v1 string?
                          :v2 integer?}
                         (exactly-keys :v1 :v2))
        user=> (built-like :X {:v1 \"apple\"})
        => Value has missing keys: #{:v2}; it is {:v1 \"apple\"}
        nil
        user=> (built-like :X {:v1 \"apple\" :v2 3})
        {:v1 \"apple\", :v2 3}
        user=> (built-like :X {:v1 \"apple\" :v2 3, :actual-is-too-big true})
        Value has extra keys: #{:actual-is-too-big}; it is {:v1 \"apple\", ...
        => nil

   
   Note: this predicate works only with keys, not paths.

   Note also: all that matters is the presence of keys, not their values."
  [& coll]
  (letfn []
    (->> (fn [actual]
           (let [[status _] (key-differences coll actual)]
             (= status :ok)))
         (show-as (cl-format nil "(exactly-keys ~{~A~^ ~})" coll))
         (explain-with (fn [oopsie]
                         (let [[status keys] (key-differences coll (:leaf-value oopsie))
                               fmt (if (= status :actual-has-extras)
                                     "%s has extra keys: %s; it is %s"
                                     "%s has missing keys: %s; it is %s")]
                         (format fmt
                                 (oopsie/friendly-path oopsie)
                                 keys
                                 (:leaf-value oopsie))))))))

(defn kvs
  "A variant of [[exactly]] that ignores the difference between records and maps.

   In Clojure, compound values are usually equal if their contents are equal. For example:

       (= (vector 1 2) (list 1 2)) ;=> true

   That is not true when comparing records to maps. Therefore, the following will *always*
   be false if `structure` is a record.

       (built-like (pred/exactly {:a 1, :b 2}) structure)

   However, the following will be true, given `(defrecord R [a b])`:

       (built-like (pred/kvs {:a 1, :b 2}) (R. 1 2))

   Note: `kvs` is false when given anything other than a map or record."
  [maplike]
  (let [expected (into {} maplike)]
    (pdef/compose-predicate
     (format "(kvs %s)" (readable/value-string expected))
     (fn [actual] (= (into {} actual) expected))
     (pdef/should-be "%s should be structurally equal to `%s`; it is `%s`" expected))))

;;; More exotic predicate creation.


(defn all-of
  "This is used with [[implies]] to group a collection of `condensed-type-descriptions`
   into one. 
   
        (all-of (requires :x :y) (includes :Point) {:color string?})
"
  [& condensed-type-descriptions]
  (type-expander/mkfn [type-map]
    (type-descriptions/canonicalize condensed-type-descriptions type-map)))

(defn ^:no-doc implies:mkfn:from-adjusted [adjusted-pairs]
  (->> (fn [exval]
         (letfn [(adjust-path [oopsie]
                   (update oopsie :path #(into (:path exval) %)))]
           (reduce (fn [so-far [antecedent consequent]]
                     (if (empty? (antecedent (:leaf-value exval)))
                       (into so-far (map adjust-path (consequent (:leaf-value exval))))
                       so-far))
                   []
                   adjusted-pairs)))
       lifting/mark-as-lifted
       (show-as "implies")))

(defn implies
   "There's enough going on with `implies` that it has its own
    page in the user documentation: http://bit.ly/1LeLTy9.

   Both the `if` and `then` parts are either a single condensed
   type description (like `(requires :a :b :c)`) or a collection 
   of them wrapped in [[all-of]]. 

   Each `if-part` is evaluated in turn. When the `if-part` matches
   the whole value, then the `then-part` is applied to check for
   type errors. Otherwise,the `then-part` is ignored.
   
        ;; If `:a` is present, `:b` must also be present:
        (type! :X (pred/implies :a :b))  

        ;; If `:a` is absent (or nil), `:b` must be odd.
        (type! :X (pred/implies {:a nil?} {:b [required-path odd?]}))

        ;; A use of `all-of`:
        (type! :X (pred/implies :a (pred/all-of (includes :Point)
                                                (requires :color))))

   Warning: this \"predicate\" cannot be used outside of a structural-typing
   functions like `type!`, `named`, and `built-like`.
"
  {:arglists '([if-part then-part if-part then-part  ...])}
  [& args]

  (type-expander/mkfn [type-map]
    (let [lift #(lifting/lift-type (vector %) type-map)]
      (implies:mkfn:from-adjusted (partition 2 (map lift args))))))

