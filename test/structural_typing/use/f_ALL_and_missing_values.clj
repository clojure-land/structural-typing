(ns structural-typing.use.f-ALL-and-missing-values
  (:require [structural-typing.preds :as pred])
  (:use midje.sweet
        structural-typing.type
        structural-typing.global-type
        structural-typing.clojure.core
        structural-typing.assist.testutil)
  (:refer-clojure :except [any?]))


(start-over!)

(fact "when there are no required paths, ALL can correspond to no value, nil, or an empty array"
  (type! :Top {[ALL] odd?})
  (built-like :Top nil) => nil
  (built-like :Top []) => []

  (type! :Bottom {[:x ALL] odd?})
  (built-like :Bottom {}) => {}
  (built-like :Bottom {:x nil}) => {:x nil}
  ;; If you want the above rejected, you do this:
  (check-for-explanations {:x required-path, [:x ALL] odd?} {:x nil}) => (just (err:value-nil :x))
  (built-like :Bottom {:x []}) => {:x []}

  (type! :Middle {[:x ALL :y] odd?})
  (built-like :Middle {}) => {}
  (built-like :Middle {:x nil}) => {:x nil}
  (built-like :Middle {:x []}) => {:x []}
  (built-like :Middle {:x [{}]}) => {:x [{}]}
  ;; Following is because an optional value allows either `nil` or "missing"
  (built-like :Middle {:x [{:y nil}]}) => {:x [{:y nil}]}
  (built-like :Middle {:x [{:y 1}]}) => {:x [{:y 1}]}
  (check-for-explanations :Middle {:x [{:y 2}]}) => (just (err:shouldbe [:x 0 :y] "odd?" 2)))


(fact "when the ALL is a required-path, it will not accept a nil value"
  (type! :Top {[ALL] [required-path odd?]})
  (check-for-explanations :Top nil) => (just (err:selector-at-nil [ALL]))
  (built-like :Top []) => []

  (let [path [:x ALL]]
    (type! :Bottom {path [required-path odd?]})
    (check-for-explanations :Bottom {}) => (just (err:missing :x))
    (check-for-explanations :Bottom {:x nil}) => (just (err:value-nil :x))
    (built-like :Bottom {:x []}) => {:x []})

  (let [path [:x ALL :y]]
    (type! :Middle {path [required-path odd?]})
    (check-for-explanations :Middle {}) => (just (err:missing :x))
    (check-for-explanations :Middle {:x nil}) => (just (err:value-nil :x))
    (built-like :Middle {:x []}) => {:x []}
    (check-for-explanations :Middle {:x [{}]}) => (just (err:missing [:x 0 :y]))
    (check-for-explanations :Middle {:x [{:y nil}]}) => (just (err:value-nil [:x 0 :y]))
    (built-like :Middle {:x [{:y 1}]}) => {:x [{:y 1}]}
    (check-for-explanations :Middle {:x [{:y 2}]}) => (just (err:shouldbe [:x 0 :y] "odd?" 2))))

(fact "when RANGE is a required-path, it will not accept a nil value"
  (type! :Top {[(RANGE 0 1)] [required-path odd?]})
  (check-for-explanations :Top nil) => (just (err:selector-at-nil [(RANGE 0 1)]))
  (check-for-explanations :Top []) => (just (err:missing [0]))

  (let [path [:x (RANGE 0 1)]]
    (type! :Bottom {path [required-path odd?]})
    (check-for-explanations :Bottom {}) => (just (err:missing :x))
    (check-for-explanations :Bottom {:x nil}) => (just (err:value-nil :x))
    (check-for-explanations :Bottom {:x []}) => (just (err:missing [:x 0])))

  (let [path [:x (RANGE 0 1) :y]]
    (type! :Middle {path [required-path odd?]})
    (check-for-explanations :Middle {}) => (just (err:missing :x))
    (check-for-explanations :Middle {:x nil}) => (just (err:value-nil :x))
    (check-for-explanations :Middle {:x []}) => (just (err:missing [:x 0]))
    (check-for-explanations :Middle {:x [{}]}) => (just (err:missing [:x 0 :y]))
    (check-for-explanations :Middle {:x [{:y nil}]}) => (just (err:value-nil [:x 0 :y]))
    (built-like :Middle {:x [{:y 1}]}) => {:x [{:y 1}]}
    (check-for-explanations :Middle {:x [{:y 2}]}) => (just (err:shouldbe [:x 0 :y] "odd?" 2))))


(start-over!)
