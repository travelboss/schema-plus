(ns schema-plus.core
  "Core functions, including defschema+"
  (:require [clojure.set :refer [difference]]
            [clojure.string :as string]
            [schema.core :as s]
            [schema-generators.generators :as sg]
            [clojure.test.check.generators :as cg]))

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

(defn process-opts
  "Only for internal usage"
  [kvs]
  (when (odd? (count kvs))
    (throw (RuntimeException. "Expected an even number of key/values")))

  (let [opts-map (apply hash-map kvs)
        extra-opts (-> opts-map
                       keys
                       set
                       (difference #{:docs :generator :example :make-builders?}))]
    (if (seq extra-opts)
      (throw (RuntimeException. (str "Got unexpected option keys: "
                                     (string/join ", " extra-opts))))
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

   (+Foo-mock-generate) - Generates a complete mock instance of Foo

   Typical usage for creating a new instance would look like:

     (-> (+Foo)
         (+Foo-with-bar 123)
         (+Foo-build))

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

  (let [opts-map (process-opts kvs)
        docstring (:docs opts-map "")
        generator-customizer (:generator opts-map)
        make-builders? (:make-builders? opts-map true)

        base-name (if (nil? (namespace schema-name))
                    (str "+" schema-name)
                    (str (namespace schema-name) "/+" (name schema-name)))

        ; build a seq of [setter-fn-name field-name] for all fields
        fn-names-and-keys (if (and make-builders? (map? schema-form))
                            (for [k (keys schema-form)]
                              (let [field-name (if (seq? k) ; remove s/optional-key wrappers, etc
                                                 (last k)
                                                 k)]
                                [(symbol (str base-name "-with-" (name field-name)))
                                 field-name]))
                            [])
        builder-fn-name (symbol base-name)
        build-fn-name (symbol (str base-name "-build"))]

    (concat
      ['do]

      (when make-builders?
        ; for each field name, defn a setter function
        (for [[fn-name schema-key] fn-names-and-keys]
          `(defn ~fn-name
             [this# v#]
             (assoc this# ~schema-key v#))))

      (list
        `(let [; make the normal defschema call
               schema-var# (s/defschema ~schema-name ~docstring ~schema-form)
               schema-obj# (var-get schema-var#)

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
                            (throw (RuntimeException. (str "Got bad generator argument for: " ~schema-name))))

               example# (if (contains? ~opts-map :example)
                          (:example ~opts-map)
                          (cg/generate generator#))

               schema-with-meta# (vary-meta
                                   schema-obj#
                                   assoc :json-schema {:description ~docstring
                                                       :example example#})

               ; redef with metadata - not sure how else to properly do this
               schema-var# (s/defschema ~schema-name ~docstring schema-with-meta#)
               schema-obj# (var-get schema-var#)]

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
             (defn ~build-fn-name
               [this#]
               (let [final# (dissoc this# ::incomplete)]
                 (s/validate schema-obj# final#)
                 final#)))

           schema-var#)))))

(defmacro +->
  "A utility threading macro for use with builder functions. Removes the need
   for a final call to +MySchema-build. Example usage:

   (+-> +Person
        (+Person-with-name \"Bob\")
        (+Person-with-age 42))"
  [start & remainder]
  (let [build-fn-name (symbol (str start "-build"))]
    `(-> (~start)
         ~@remainder
         (~build-fn-name))))

(defmacro +generate->
  "A utility threading macro for creating mock data. Starts with a generated
   instance of the schema, and allows fields to be customized. There is no
   need for a final -build call. Example usage:

   (+generate-> +Person
                (+Person-with-age 42))"
  [start & remainder]
  (let [schema-name (.substring (name start) 1) ; strip off namespace, leading '+'
        schema-name-with-ns (if (nil? (namespace start))
                              schema-name
                              (str (namespace start) "/" schema-name))
        schema-symbol (symbol schema-name-with-ns)
        build-fn-name (symbol (str start "-build"))]

  `(-> (~start (generate ~schema-symbol))
       ~@remainder
       (~build-fn-name))))
