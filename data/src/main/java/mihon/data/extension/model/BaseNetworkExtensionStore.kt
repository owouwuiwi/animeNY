package mihon.data.extension.model

import mihon.domain.extension.model.ExtensionStore

interface BaseNetworkExtensionStore {
    fun toExtensionStore(indexUrl: String): ExtensionStore
}

interface ModernNetworkExtensionStore<Entries> : BaseNetworkExtensionStore {
    val extensionList: Entries?
    val extensionListUrl: String?
}
