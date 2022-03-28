# funcify-feature-eng-naming

## Objective

* Provide a way of standardizing naming of components across various contexts for various input types
* Explore the notion of having "naming conventions" and devising a straightforward way of creating them through **DSLs** (=> domain-specific
  languages), which in this case is the language associated with _string_ and _character_ transformations and their _positions_ e.g. _leading
  characters_ and _categories_ e.g. _uppercase_

## Example

### Input: 
* Type: **KClass<\*\>**

```kotlin
val kClassNamingConvention: NamingConvention<KClass<*>> = DefaultNamingConventionFactory.getInstance()
        .createConventionFor<KClass<*>>()
        .whenInputProvided {
          treatAsOneSegment { kc: KClass<*> ->
            kc.qualifiedName
            ?: kc.jvmName
          }
        }
        .followConvention {
          forEachSegment {
            forAnyCharacter {
              transformByWindow {
                anyCharacter { c: Char -> c.isUpperCase() }.precededBy { c: Char -> c.isLowerCase() }
                        .transformInto { c: Char -> "_$c" }
              }
              transformByWindow {
                anyCharacter { c: Char -> c.isDigit() }.precededBy { c: Char -> c.isLowerCase() }
                        .transformInto { c: Char -> "_$c" }
              }
              transformAll { c: Char -> c.lowercaseChar() }
            }
          }
          furtherSegmentAnyWith('_')
          joinSegmentsWith('_')
        }
        .named("KClassSnakeCase")

println(kClassNamingConvention.deriveName(StandardNamingConventions::class))
```

```kotlin
"funcify.naming.standard_naming_conventions"
```