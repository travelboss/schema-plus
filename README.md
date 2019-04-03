# schema-plus
Adds easy mock generation and builder functions to plumatic/schema definitions.

## Usage

The main function you'll use is `schema-plus.core/defschema+`. Use this instead of Plumatic's `schema/defschema` to get mock generation capabilities, default example values for Swagger, and optional builder functions.

A docstring (and documentation for usage in Swagger) can be supplied by passing an optional `:docs` argument followed by the string.

Based on the types used in the schema definition, a `clojure.test.check.generators.Generator` instance will be created that can generate mock data satisfying the schema definition. This automatically handles any referenced schemas that were also created with defschema+.

The default generator behavior can be replaced or augmented by supplying a `:generator` option. The following argument should either be a `clojure.test.check.generators.Generator` instance (to totally replace the default generator) or a function accepting one argument. If a function is passed, it will be passed as the first argument to `clojure.test.check.generators/fmap`, with the default generator as the second argument. In other words, when generating, the output of the default generator will be passed to your custom function as the only argument.

Example of supplying a custom generator:

```clojure
(defschema+ Person
  {:name schema/Str
   :age schema/Int}
  :generator
  (gen/return {:name \"Bob\", :age 42}))
```

Example of supplying an fmap function:

```clojure
(defschema+ Person
  {:name schema/Str
   :age schema/Int}
  :generator
  (fn [default-generated] (assoc default-generated :age 42)))
```

If `:make-builders?` is not supplied, or is followed by `true`, then default builder functions will be defined. This will automatically define the following functions in the current namespace (assuming the schema-name is `Foo` and there is a field named `:bar`):

```
(+Foo) - Create an initial, empty instance

(+Foo m) - Create an initial instance populated with fields populated from a map

(+Foo-with-bar this v) - set the `:bar` field on a Foo instance

(+Foo-build this) - Finalize creation of a Foo instance
```

Typical usage for creating a new instance would look like:

```clojure
(-> (+Foo)
    (+Foo-with-bar 123)
    (+Foo-build))
```

Full example with all options:

```clojure
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
```

Once `defschema+` has been used to define a schema, `schema-plus.core/generate` can be used to generate mock data that satisfies that schema. This includes handling nested references to other schema definitions (assuming they were also defined with `defschema+`):

```
=> (schema-plus.core/generate Person)
{:name "Efi239af", :age 1324}
```
