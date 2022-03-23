package funcify.naming.charseq.template

import funcify.naming.charseq.context.IndexedChar
import funcify.naming.charseq.context.IndexedCharExtensions.at
import funcify.naming.charseq.group.CharGroup
import funcify.naming.charseq.group.BaseCharGroup
import funcify.naming.charseq.group.DelimiterCharGroup
import funcify.naming.charseq.operation.CharSeqFunctionContext
import funcify.naming.charseq.spliterator.DelimiterGroupingSpliterator
import funcify.naming.charseq.spliterator.DuplicatingSpliterator
import java.util.stream.Stream
import java.util.stream.StreamSupport


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
interface CharSeqStreamBasedTemplate : CharSequenceContextTransformationTemplate<CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>>, Stream<IndexedChar>, Stream<CharGroup>> {

//
//    override fun filterCharacters(context: CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>>,
//                                  filter: (Char) -> Boolean): CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>> {
//        return context.mapCharSeq({ cs -> cs.filter { (c, _) -> filter.invoke(c) } },
//                                  { csi ->
//                                      csi.flatMap({ ccg ->
//                                                      StreamSupport.stream(ccg.groupSpliterator,
//                                                                           false)
//                                                  })
//                                              .filter({ (c, _) -> filter.invoke(c) })
//                                  })
//    }
//
//    override fun filterCharactersWithIndex(context: CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>>,
//                                           filter: (Int, Char) -> Boolean): CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>> {
//        return context.mapCharSeq({ cs ->
//                                      cs.filter { (c, idx) ->
//                                          filter.invoke(idx,
//                                                        c)
//                                      }
//                                  },
//                                  { csi ->
//                                      csi.flatMap({ ccg ->
//                                                      StreamSupport.stream(ccg.groupSpliterator,
//                                                                           false)
//                                                  })
//                                              .filter({ (c, idx) ->
//                                                          filter.invoke(idx,
//                                                                        c)
//                                                      })
//                                  })
//    }
//
//    override fun mapCharacters(context: CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>>,
//                               mapper: (Char) -> Char): CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>> {
//        return context.mapCharSeq({ cs -> cs.map { (c, idx) -> mapper.invoke(c) at idx } },
//                                  { csi ->
//                                      csi.flatMap({ ccg ->
//                                                      StreamSupport.stream(ccg.groupSpliterator,
//                                                                           false)
//                                                  })
//                                              .map({ (c, idx) -> mapper.invoke(c) at idx })
//                                  })
//    }
//
//    override fun mapCharactersWithIndex(context: CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>>,
//                                        mapper: (Int, Char) -> Char): CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>> {
//        return context.mapCharSeq({ cs ->
//                                      cs.map { (c, idx) ->
//                                          mapper.invoke(idx,
//                                                        c) at idx
//                                      }
//                                  },
//                                  { csi ->
//                                      csi.flatMap({ ccg ->
//                                                      StreamSupport.stream(ccg.groupSpliterator,
//                                                                           false)
//                                                  })
//                                              .map({ (c, idx) ->
//                                                       mapper.invoke(idx,
//                                                                     c) at idx
//                                                   })
//                                  })
//    }
//
//    override fun groupCharactersByDelimiter(context: CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>>,
//                                            delimiter: Char): CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>> {
//        return context.mapCharSeqIterable({ cs ->
//                                              StreamSupport.stream(DelimiterGroupingSpliterator(cs.spliterator(),
//                                                                                                { c -> c == delimiter }),
//                                                                   false)
//                                          },
//                                          { csi ->
//                                              /**
//                                               * - Duplicate given spliterator
//                                               * - Take first two groups from duplicate
//                                               * - If one of the first two groups is a delimiter char group, convert that group
//                                               * into a string and compare against the input delimiter
//                                               * - If the current delimiter can be determined to not be the same as
//                                               * the input delimiter, flatten char groups and regroup using new delimiter
//                                               * - If the current delimiter matches or current grouping type is not necessarily
//                                               * a delimited grouping type, keep current char groupings and return as stream
//                                               */
//                                              val splitr1 = DuplicatingSpliterator(csi.spliterator())
//                                              val splitr2 = splitr1.duplicate()
//                                              var counter = 0
//                                              val delimiterOrDelimitedCharGroupHolder = arrayOfNulls<CharGroup>(1)
//                                              while (counter < 2 && splitr2.tryAdvance({ cg ->
//                                                                                           delimiterOrDelimitedCharGroupHolder[0] = cg
//                                                                                       }) && delimiterOrDelimitedCharGroupHolder[0] !is DelimiterCharGroup) {
//                                                  counter++
//                                              }
//                                              if (delimiterOrDelimitedCharGroupHolder[0] is DelimiterCharGroup && delimiterOrDelimitedCharGroupHolder[0].toString() != delimiter.toString()) {
//                                                  StreamSupport.stream(DelimiterGroupingSpliterator(StreamSupport.stream(splitr1,
//                                                                                                                         false)
//                                                                                                            .flatMap { cg ->
//                                                                                                                StreamSupport.stream(cg.groupSpliterator,
//                                                                                                                                     false)
//                                                                                                            }
//                                                                                                            .spliterator(),
//                                                                                                    { c -> c == delimiter }),
//                                                                       false)
//                                              } else {
//                                                  StreamSupport.stream(splitr1,
//                                                                       false)
//                                              }
//                                          })
//    }
//
//    override fun mapCharacterSequence(context: CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>>,
//                                      mapper: (Stream<CharGroup>) -> Stream<CharGroup>): CharSeqFunctionContext<Stream<IndexedChar>, Stream<CharGroup>> {
//        return context.mapCharSeqIterable({ cs ->
//                                              /**
//                                               * Make single char group stream in this case
//                                               */
//                                              mapper.invoke(Stream.of(BaseCharGroup(cs.spliterator())))
//                                          },
//                                          { csi ->
//                                              mapper.invoke(csi)
//                                          })
//    }
}