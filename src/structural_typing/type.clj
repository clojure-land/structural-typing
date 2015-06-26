(ns structural-typing.type
  "Structural types, loosely inspired by Elm's way of looking at [records](http://elm-lang.org/learn/Records.elm)."
  (:require [structural-typing.api.type-repo :as repo]
            [structural-typing.global-type :as global-type]
            [potemkin.namespaces :as ns]))

(ns/import-vars [structural-typing.api.predicates
                 show-as
                 explain-with
                 required-key
                 member])

(ns/import-vars [structural-typing.api.type-repo
                 empty-type-repo
                 replace-error-handler
                 replace-success-handler])
      
(ns/import-vars [structural-typing.api.path
                 includes
                 ALL])

(ns/import-vars [structural-typing.api.defaults
                 throwing-error-handler
                 default-error-handler
                 default-success-handler])


;;;; 

(defn- all-oopsies [type-repo one-or-more candidate]
  (let [signifiers (if (sequential? one-or-more) one-or-more (vector one-or-more))]
    (mapcat #(repo/oopsies type-repo % candidate) signifiers)))

(defn checked
  "Check the map `candidate` against the previously-defined type named by `type-signifier` in
  the given `type-repo`. If the `type-repo` is omitted, the global one is used.
   
       (type/checked :Point {:x 1 :y 2})

   To check if a candidate matches all of a set of types, wrap them in a vector:

       (type/checked [:Colorful :Point] {:x 1, :y 2, :color \"red\"})
   
   Types are defined with [[named]] or [[named!]]. By default, `checked` returns
   the `candidate` argument if it checks out, `nil` otherwise. Those defaults can be
   changed.
"
  ([type-repo type-signifier candidate]
     (let [oopsies (all-oopsies type-repo type-signifier candidate)]
       (if (empty? oopsies)
         ((repo/the-success-handler type-repo) candidate)
         (->> oopsies
              (sort-by :leaf-index)
              ((repo/the-error-handler type-repo))))))

  ([type-signifier candidate]
     (checked @global-type/repo type-signifier candidate)))


(defn named 
  "Define the type `type-signifier` as being a map or record containing all of the
   given `type-descriptions` (which may a vector of required keys or paths
   or a (potentially nested) map from keys/paths to predicates or vectors of predicates.

  Returns the augmented `type-repo`. See also [[named!]].
"
  ([type-repo type-signifier & type-descriptions]
     (repo/hold-type type-repo type-signifier type-descriptions)))

  
(defn described-by? 
  "Return `true` iff the map or record `candidate` typechecks against
   the type named `type-signifier` (or vector of signifiers).

   With three arguments, the check is against the `type-repo`. If `type-repo` is
   omitted, the global repo is used.
   
       (type/described-by? :Point candidate)
       (type/described-by? [:Colorful :Point] candidate)
"
  ([type-repo type-signifier candidate]
     (empty? (all-oopsies type-repo type-signifier candidate)))
  ([type-signifier candidate]
     (described-by? @global-type/repo type-signifier candidate)))


(def required-paths 
  "Used to create an argument to `named`. All of the elements are keys or paths
   that are required (as with `[required-key`) to be present in any matching
   candidate. This is exactly the same thing as putting the arguments in a vector."
vector)

;; (defn coercion 
;;   "Register function `f` as one that can coerce a map or record into 
;;    one that matches type `type-signifier`. The updated `type-repo` is returned.
;;    See also [[coercion!]], which updates the global type repo."
;;   [type-repo type-signifier f]
;;   (checked-internal own-types :type-repo type-repo)
;;   (assoc-in type-repo [:coercions type-signifier] f))

;; (defn coerced
;;   "Coerce the map or record `candidate` into the type named `type-signifier` in the `type-repo`
;;    and check the result with [[checked]]. The coerced version of `candidate` is returned.
   
;;    If the type repo is omitted, the global type repo is used.
;;    Coercions are defined with [[coercion]] or [[coercion!]].
   
;;         (some-> (coerce :user-v2 legacy-json)
;;                 (update-in [:stats :logins] inc))

;;    If `type-signifier` hasn't been defined (via [[name]] or [[named!]]), the final
;;    call to `checked` is omitted.
;; "
;;   ([type-repo type-signifier candidate]
;;      (checked-internal own-types :type-repo type-repo)
;;      (let [coercer (get-in type-repo [:coercions type-signifier] identity)]
;;        (->> (coercer candidate)
;;             (checked-internal type-repo type-signifier))))
;;   ([type-signifier candidate]
;;      (coerced @stages/global-type-repo type-signifier candidate)))

