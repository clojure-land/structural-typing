(ns structural-typing.use.condensed-type-descriptions.f-requires-mentioned-paths
  (:require [structural-typing.preds :as pred])
  (:use midje.sweet
        structural-typing.type
        structural-typing.global-type
        structural-typing.clojure.core
        structural-typing.assist.testutil)
  (:refer-clojure :except [any?]))


(start-over!)

(fact "simple cases"
  (fact "no condensed type descriptions"
    (type! :X (requires-mentioned-paths))
    (built-like :X {}) => {}
    (built-like :X {:whatever 3}) => {:whatever 3})
  (fact "a simple key"
    (type! :X (requires-mentioned-paths {:x integer?}))
    (built-like :X {:x 3}) => {:x 3}
    (check-for-explanations :X {:x :not-int}) => (just (err:shouldbe :x "integer?" :not-int))
    (check-for-explanations :X {}) => (just (err:missing :x)))
  (fact "a path"
    (type! :X (requires-mentioned-paths {:x {:y integer?}}))
    (check-for-explanations :X {:x 3}) => (just (err:not-maplike [:x :y] 3))
    (check-for-explanations :X {:x {:y :not-int}}) => (just (err:shouldbe [:x :y] "integer?" :not-int))
    (check-for-explanations :X {}) => (just (err:missing :x))))
    


(fact "use with includes"
  (type! :Point {:x integer?, :y integer?})
  (type! :X (requires-mentioned-paths {[:points ALL] (includes :Point)}))

  (built-like :X {:points [{:x 1 :y 1}]}) => {:points [{:x 1 :y 1}]}
  (check-for-explanations :X {:points [{:x 1}]}) => (just (err:missing [:points 0 :y]))
  
  (fact "use with an `implies` that uses `includes`: the `includes` works"
    (type! :Point {:x integer? :y integer?})
    
    (fact "(but *contents* of implies are not part of a path)"
      (type! :Include (requires-mentioned-paths (pred/implies :c (includes :Point))))

      (built-like :Include {}) => {}
      (built-like :Include {:x :ok-not-to-be-int}) => {:x :ok-not-to-be-int}
      (check-for-explanations :Include {:c 1 :x :ok-not-to-be-int})
      => (just (err:shouldbe :x "integer?" :ok-not-to-be-int)))


    (fact "deeper nesting"
      (type! :X (requires-mentioned-paths {:l3 integer?}
                                          (pred/implies :l4 (includes :Include))))
      (built-like :X {:l3 3}) => {:l3 3}
      (check-for-explanations :X {}) => (just (err:missing :l3))

      ;; The rest illustrate that deep nesting of included types is unaffected by `requires-mentioned-paths
      (built-like :X {:l3 3, :l4 true}) => {:l3 3, :l4 true}
      (built-like :X {:l3 3, :l4 true, :c true}) => {:l3 3, :l4 true, :c true}
      (built-like :X {:l3 3, :l4 true, :c true, :x 1, :y 2}) => {:l3 3, :l4 true, :c true, :x 1, :y 2}
                 
      (check-for-explanations :X {:l3 3, :l4 true, :c true, :x :notint, :y :notint})
      => (just (err:shouldbe :x "integer?" :notint)
               (err:shouldbe :y "integer?" :notint)) )))
          

(fact "typical use is to force paths in a previous type to be required"
  (type! :P {:x integer? :y {:z string?}})
  (type! :X (requires-mentioned-paths (includes :P)))

  (built-like :X {:x 1 :y {:z "foo"}}) => {:x 1 :y {:z "foo"}}
  (check-for-explanations :X {:x 1 :y {}}) => (just (err:missing [:y :z])))
  
(fact "wrapping a part of group of condensed type descriptions"
  (type! :Point {:x integer? :y integer?})
  (type! :X
         (requires-mentioned-paths (includes :Point))
         (requires-mentioned-paths {:color string? :hue string?})
         {:other integer?})
  (let [in {:x 1 :y 2 :color "red" :hue "dark"}]
    (built-like :X in) => in)
  (check-for-explanations :X {}) => (just (err:missing :color)
                                          (err:missing :hue)
                                          (err:missing :x)
                                          (err:missing :y))
  (check-for-explanations :X {:x 1 :y 2 :color "red" :hue "dark" :other :non-int})
  => (just (err:shouldbe :other "integer?" :non-int)))

(fact "duplicate required-path are not affected"
  (type! :X (requires-mentioned-paths (requires :x)
                                {:x [integer?] :y [required-path]}
                                (requires :z)))
  (built-like :X {:x 3 :y 4 :z 5}) => {:x 3 :y 4 :z 5}
  (check-for-explanations :X {:x :not-int}) => (just (err:shouldbe :x "integer?" :not-int)
                                                     (err:missing :y)
                                                     (err:missing :z))
  (check-for-explanations :X {}) => (just (err:missing :x)
                                          (err:missing :y)
                                          (err:missing :z)))

(start-over!)

