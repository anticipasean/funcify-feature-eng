package funcify.feature.json.design

import funcify.feature.json.KJson
import funcify.feature.json.container.KJsonContainerFactory.KJsonContainer
import funcify.feature.json.template.KJsonTemplate

internal interface KJsonDesign<SWT, I> : KJson {

    val template: KJsonTemplate<SWT>



    fun <WT> fold(template: KJsonTemplate<WT>): KJsonContainer<WT, I>

}
