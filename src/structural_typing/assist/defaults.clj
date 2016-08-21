(ns structural-typing.assist.defaults
  "User-visible default behaviors.

   Much of this is gathered into the catchall `structural-typing.types` namespace."
  (:use structural-typing.clojure.core)  ; yes: `use`. Glorious, skimmable, terse `use`.
  (:require [structural-typing.assist.oopsie :as oopsie]
            [structural-typing.assist.format :as format])
  (:refer-clojure :exclude [any?]))

(defn default-predicate-explainer
  "Converts an [[oopsie]] into a string of the form \"%s should be `%s`; it is `%s`\"."
  [{:keys [predicate-string leaf-value] :as expred}]
  (format "%s should be `%s`; it is %s"
          (oopsie/friendly-path expred)
          predicate-string
          (format/leaf leaf-value)))

(def default-success-handler 
  "The default success handler just returns the original candidate structure passed to `built-like`."
  identity)

(def default-error-handler
  "This error handler takes the output of type checking (a sequence of [[oopsies]]) and prints
   each one's explanation to standard output. It returns
   `nil`, allowing constructs like this:
   
        (some-> (type/built-like :Patient p)
                (assoc :handled true)
                ...)
"
  (oopsie/mkfn:apply-to-each-explanation println))

(defn throwing-error-handler 
  "In contrast to the default error handler, this one throws a
   `java.lang.Exception` whose message is the concatenation of the
   [[explanations]] of the [[oopsies]].
   
   To make all type mismatches throw failures, do this:
   
          (global-type/on-error! type/throwing-error-handler) ; for the global type repo
          (type/replace-error-handler type-repo type/throwing-error-handler) ; local repo
"
  [oopsies]
  (throw (new Exception (str-join "\n" (oopsie/explanations oopsies)))))
