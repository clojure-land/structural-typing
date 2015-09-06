(ns structural-typing.assist.type-repo
  "The `TypeRepo` structure and its functions."
  (:use structural-typing.clojure.core)
  (:require [structural-typing.guts.type-descriptions.canonicalizing :as canon]
            [structural-typing.guts.preds.from-type-descriptions :as compile]
            [structural-typing.defaults :as default]
            [structural-typing.guts.preds.core :as pred]))


;; This is used to check if an argument to `checked` is nil. If so, it's not further
;; checked. Another approach would be to inject the following map into all types when
;; they're compiled. However, that would mean that the `T1` in:
;;     (type! :T2 {:a (includes :T1)})
;; ... would not be optional, which would make it different from all other pred-like values.
(def whole-type-checker (compile/compile-type {[] [pred/not-nil]}))

(defprotocol TypeRepoLike
  (hold-type [type-repo type-signifier type-descriptions])
  (check-type [type-repo type-signifier candidate])
  (replace-success-handler [type-repo handler]
    "For this `type-repo`, handle candidates that typecheck successfully by 
     passing them to `handler` as the last step in [[checked]]..")
  (replace-error-handler [type-repo handler]
    "For this `type-repo`, pass [[oopsies]] generated by type failures to 
     `handler` as the last step in [[checked]].")
  (the-success-handler [type-repo])
  (the-error-handler [type-repo]))

(defrecord TypeRepo [success-handler error-handler]
    TypeRepoLike
    (hold-type [type-repo type-signifier type-descriptions]
      (let [canonicalized (apply canon/canonicalize
                                 (:canonicalized-type-descriptions type-repo)
                                 type-descriptions)
            compiled (compile/compile-type canonicalized)]
        (-> type-repo 
            (assoc-in [:original-type-descriptions type-signifier] type-descriptions)
            (assoc-in [:canonicalized-type-descriptions type-signifier] canonicalized)
            (assoc-in [:compiled-types type-signifier] compiled))))

    (check-type [type-repo type-signifier candidate]
      (if-let [checker (get-in type-repo [:compiled-types type-signifier])]
        (let [oopsies (whole-type-checker candidate)]
          (if (empty? oopsies)
            (checker candidate)
            oopsies))
        (boom! "There is no type `%s`" type-signifier)))
    
    (replace-error-handler [type-repo f]
      (assoc type-repo :error-handler f))

    (replace-success-handler [type-repo f]
      (assoc type-repo :success-handler f))

    (the-error-handler [type-repo] (:error-handler type-repo))
    (the-success-handler [type-repo] (:success-handler type-repo)))

(defmethod clojure.core/print-method TypeRepo [o, ^java.io.Writer w]
  (.write w "#TypeRepo[")
  (.write w (->> o :original-type-descriptions keys (str-join ", ")))
  (.write w "]"))
          

(def empty-type-repo
  "A type repo that contains no types and uses the default success and error handlers."
  (->TypeRepo default/default-success-handler default/default-error-handler))

(defn origin
  "Returns the original description of the `type-signifier` (a sequence of vectors and maps)"
  [type-repo type-signifier]
  (get-in type-repo [:original-type-descriptions type-signifier]))

(defn description
  "Returns the canonical (expanded) description of the `type-signifier`."
  [type-repo type-signifier]
  (get-in type-repo [:canonicalized-type-descriptions type-signifier]))
