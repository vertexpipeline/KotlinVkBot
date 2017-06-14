fun main(args: Array<String>) {
    val token = "5b4fd2e1d91f429f568504cb9bb1f553066ea7f95bf906e5589b90dbf0643055cd1faf066dc25171d2cc2"
    val api = VKApi(token)
    api.ChatMessageWritten = {
        when(it.message) {
            "бот, время" -> {
                val date = api.getServerTime().toString()
                api.sendChatMessage(it.chat_id, date)
            }
            "бот, кто я?" ->{
                api.sendChatMessage(it.chat_id, api.currentVKUser.status)
            }
            "бот, напиши мне"-> {
                api.sendUserMessage(arrayOf(api.currentVKUser.id), "kek")
            }
        }
    }
    api.listenLongPool()
}