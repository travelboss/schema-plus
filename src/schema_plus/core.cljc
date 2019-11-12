(ns schema-plus.core
  "Core functions, including defschema+"
  #?(:cljs (:require-macros [schema-plus.core]))
  (:require [clojure.set :refer [difference]]
            [clojure.string :as string]
            [schema.core :as s]
            [schema-generators.generators :as sg]
            [clojure.test.check.generators :as cg]
            #?(:cljs [schema.core :refer [Constrained]]))
  #?(:clj (:import [schema.core Constrained])))

(def generator-registry
  "Holds a map of Schema -> Generator for default generation behavior."
  (atom {}))

(defn generate
  "Generate a value from a schema."
  [schema-obj]
  (sg/generate schema-obj @generator-registry))

(defn get-generator
  "Get the current clojure.test.check.generators.Generator for a schema."
  [schema-obj]
  (get @generator-registry schema-obj))

(defn set-generator
  "Manually set the Generators for some schema object. Normally this
   should only be used for raw Java Classes and other objects where
   defschema+ can't be used directly."
  [schema-obj generator]
  (swap! generator-registry assoc schema-obj generator))

(defn throw-arg-err!
  [msg]
  #?(:clj (throw (IllegalArgumentException. msg))
     :cljs (throw (js/Error. msg))))

(defn process-opts
  "Only for internal usage"
  [kvs schema-name]
  (when (odd? (count kvs))
    (throw-arg-err! (str "Bad call to defschema+, got an odd number of key/values for "
                         schema-name)))

  (let [opts-map (apply hash-map kvs)
        extra-opts (-> opts-map
                       keys
                       set
                       (difference #{:docs :generator :example :make-builders?}))]
    (if (seq extra-opts)
      (throw-arg-err! (str "Bad call to defschema+, got unexpected option keys for "
                           schema-name " :" (string/join ", " extra-opts)))
      opts-map)))

(defmacro defschema+
  "Use this instead of defschema to get mock generation capabilities,
   default example values for Swagger, and optional builder functions.

   A docstring (and documentation for usage in Swagger) can be supplied by
   passing an optional `:docs` argument followed by the string.

   Based on the types used in the schema definition, a `clojure.test.check.generators.Generator`
   instance will be created that can generate mock data satisfying the schema
   definition. This automatically handles any referenced schemas that were also
   created with defschema+.

   The default generator behavior can be replaced or augmented by supplying a `:generator`
   option. The following argument should either be a `clojure.test.check.generators.Generator`
   instance (to totally replace the default generator) or a function accepting one argument. If
   a function is passed, it will be passed as the first argument to `clojure.test.check.generators/fmap`,
   with the default generator as the second argument. In other words, when generating, the output
   of the default generator will be passed to your custom function as the only argument.

   Example of supplying a custom generator:

     (defschema+ Person
       {:name schema/Str
        :age schema/Int}
       :generator
       (gen/return {:name \"Bob\", :age 42}))

   Example of supplying an fmap function:

     (defschema+ Person
       {:name schema/Str
        :age schema/Int}
       :generator
       (fn [default-generated] (assoc default-generated :age 42)))

   If :make-builders? is not supplied, or is followed by `true`, then default builder functions
   will be defined. This will automatically define the following functions in the current
   namespace (assuming the schema-name is `Foo` and there is a field named `:bar`):

   (+Foo) - Create an initial, empty instance
   (+Foo m) - Create an initial instance populated with fields populated from a map

   (+Foo-with-bar this v) - set the `:bar` field on a Foo instance

   (+Foo-build this) - Finalize creation of a Foo instance

   (+Foo-> m forms) - (macro) Thread m through forms and validate the final result against the schema

   Typical usage for creating a new instance would look like (using the convenience macro):

     (+Foo-> (+Foo)
             (+Foo-with-bar 123))

   Full example with all options:

     (require '[schema-plus.core :refer [defschema+]])
     (require '[schema.core :as schema])
     (require '[clojure.test.check.generators :as gen])

     (defschema+ Person
       {:name schema/Str
        :age schema/Int}
       :doc \"A person with a name and age\"
       :generator (gen/hash-map :name gen/char-alphanumeric
                                :age gen/nat)
       :example {:name \"Bob\", :age 42}
       :make-builders? false)"

  [schema-name schema-form & kvs]

  (let [schema-name-str (str schema-name)
        opts-map (process-opts kvs schema-name-str)
        docstring (:docs opts-map "")
        generator-customizer (:generator opts-map)
        make-builders? (:make-builders? opts-map true)


        base-name (if (nil? (namespace schema-name))
                    (str "+" schema-name)
                    (str (namespace schema-name) "/+" (name schema-name)))

        ; don't eval in case of clojurescript, TODO: should find a better way of disabling this
        clojurescript? (-> &env :ns nil? not)
        possibly-constrained-form (if clojurescript? schema-form (eval schema-form))
        real-form (if (instance? Constrained possibly-constrained-form)
                    (:schema possibly-constrained-form)
                    possibly-constrained-form)
        ; build a seq of [setter-fn-name field-name] for all fields
        fn-names-and-keys (remove
                            nil?
                            (if (and make-builders? (map? real-form))
                              (for [k (keys real-form)]
                                (when (s/specific-key? k)
                                  [(symbol (str base-name "-with-" (name (s/explicit-schema-key k))))
                                  (s/explicit-schema-key k)]))
                            []))
        builder-fn-name (symbol base-name)
        build-fn-name (symbol (str base-name "-build"))
        build-macro-name (symbol (str base-name "->"))]

    (concat
      ['do]

      (when make-builders?
        ; for each field name, defn a setter function
        (for [[fn-name schema-key] fn-names-and-keys]
          `(defn ~fn-name
             [this# v#]
             (assoc this# ~schema-key v#))))

      (list
        `(let [; evaluate the schema body
               schema-obj# ~schema-form

               ; a naive generator simply based on types
               basic-generator# (sg/generator schema-obj# @generator-registry)

               generator# (cond
                            ; nothing passed in, use default generator behavior
                            (nil? ~generator-customizer)
                            basic-generator#

                            ; generator passed, use directly
                            (cg/generator? ~generator-customizer)
                            ~generator-customizer

                            ; function passed, make new generator with fmap
                            (fn? ~generator-customizer)
                            (cg/fmap ~generator-customizer basic-generator#)

                            :else
                            (throw-arg-err!
                              (str "Bad call to defschema+, expected Generator or fn for "
                                   ~schema-name-str " :generator, got: " (type ~generator-customizer))))

               example# (if (contains? ~opts-map :example)
                          (:example ~opts-map)
                          (cg/generate generator#))

               schema-with-meta# (vary-meta
                                   schema-obj#
                                   assoc :json-schema {:description ~docstring
                                                       :example example#})]

           (s/validate schema-obj# example#)

           ; update the registry with the new generator
           (set-generator schema-obj# generator#)

           (when ~make-builders?

             ; define function to start building instances
             (defn ~builder-fn-name
               ([] ; from scratch
                {::incomplete true})
               ([m#] ; from an initial map
                (assoc m# ::incomplete true)))

             ; define function to finish building instances
             (let [build-fn# (defn ~build-fn-name
                               [this#]
                               (let [final# (dissoc this# ::incomplete)]
                                 (s/validate schema-obj# final#)
                                 final#))]
              (defmacro ~build-macro-name
                ([~'& forms#]
                  `(~build-fn# (-> ~@forms#))))))

           (def
             ; the export metadata prevents minification of schema names during
             ; clojurescript compilation
             ~(with-meta schema-name {:export true})
             schema-with-meta#))))))
