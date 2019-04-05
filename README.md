# schema-plus
Adds easy mock generation and builder functions to [plumatic/schema](https://github.com/plumatic/schema) definitions.

## Installation

This library is available through clojars. Add the following to your dependencies:

[![Clojars Project](http://clojars.org/travelboss/schema-plus/latest-version.svg)](http://clojars.org/travelboss/schema-plus)

## Usage

The main function you'll use is `schema-plus.core/defschema+`. Use this instead of Plumatic's `schema/defschema` to get mock generation capabilities, default example values for Ring-Swagger, and optional builder functions.

A docstring (and documentation for usage in Ring-Swagger) can be supplied by passing an optional `:docs` argument followed by the string.

Based on the types used in the schema definition, a `clojure.test.check.generators.Generator` instance will be created that can generate mock data satisfying the schema definition. This automatically handles any referenced schemas that were also created with defschema+.

The default generator behavior can be replaced or augmented by supplying a `:generator` option. The following argument should either be a `clojure.test.check.generators.Generator` instance (to totally replace the default generator) or a function accepting one argument. If a function is passed, it will be passed as the first argument to `clojure.test.check.generators/fmap`, with the default generator as the second argument. In other words, when generating, the output of the default generator will be passed to your custom function as the only argument.

Example of supplying a custom generator:

```clojure
(defschema+ Person
  {:name schema/Str
   :age schema/Int}
  :generator
  (gen/return {:name "Bob" :age 42}))
```

Example of supplying an fmap function:

```clojure
(defschema+ Person
  {:name schema/Str
   :age schema/Int}
  :generator
  (fn [default-generated] (assoc default-generated :age 42)))
```

Full example with all options:

```clojure
(require '[schema-plus.core :refer [defschema+]])
(require '[schema.core :as schema])
(require '[clojure.test.check.generators :as gen])

(defschema+ Person
  {:name schema/Str
   :age schema/Int}
  :docs "A person with a name and age"
  :generator (gen/hash-map :name gen/char-alphanumeric
                           :age gen/nat)
  :example {:name "Bob" :age 42}
  :make-builders? false)"
```

### Generating

Once `defschema+` has been used to define a schema, `schema-plus.core/generate` can be used to generate mock data that satisfies that schema. This includes handling nested references to other schema definitions (assuming they were also defined with `defschema+`):

```
=> (schema-plus.core/generate Person)
{:name "Efi239af" :age 1324}
```

If you need direct access to the `Generator` for a particular schema, you can use `get-generator`:

```
=> (schema-plus.core/get-generator Person)
#clojure.test.check.generators.Generator{:gen #object[clojure.test.check.generators$gen_fmap$fn__3647 0x1513d1e2 "clojure.test.check.generators$gen_fmap$fn__3647@1513d1e2"]}
```

If you need to set the `Generator` to use for a particular schema, Java Class, or something else that `defschema+` couldn't be used for directly, you can use `set-generator`:

```
=> (schema-plus.core/set-generator Object (clojure.test.check.generators/return "abc"))
...
=> (schema-plus.core/defschema+ Bar {:a Object})
#'user/Bar
=> (schema-plus.core/generate Bar)
{:a "abc"}
```

### Builder Functions

If `:make-builders?` is not supplied, or is followed by `true`, then default builder functions will be defined. This will automatically define the following functions in the current namespace (assuming the schema-name is `Foo` and there is a field named `:bar`):

```
(+Foo) - Create an initial, empty instance

(+Foo m) - Create an initial instance populated with fields populated from a map

(+Foo-with-bar this v) - set the `:bar` field on a Foo instance

(+Foo-build this) - Finalize creation of a Foo instance, validate against the schema
```

The basic way to a new instance would look like:

```clojure
(-> (+Foo)
    (+Foo-with-bar 123)
    (+Foo-with-baz "ABC")
    (+Foo-build))
```

To make this a little less redundant, the `+->` threading macro is available. It removes the need for the final build call:

```clojure
(+-> +Foo
     (+Foo-with-bar 123)
     (+Foo-with-baz "ABC"))
```

There's also a `+generate->` version that starts with a generated instance of the schema and simply allows you to modify individual fields. This is useful for tests, where mock data is fine for most fields, but one or two need to be specified explicitly.

```clojure
(+generate-> +Foo
             (+Foo-with-baz "ABC"))
```
