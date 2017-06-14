import java.net.URL
import com.beust.klaxon.*
import java.net.URLEncoder
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList

class VKApi(val token:String, var isLPListening:Boolean = false) {
    val userFields = "nickname,screen_name,sex,bdate,city,country,timezone,photo,photo_medium,photo_big,has_mobile,contacts,education,online,counters,relation,last_seen,activity,can_write_private_message,can_see_all_posts,can_post,universities,status"
    //+events
    var ChatMessageWritten: (messageInfo:LongPoolChatMessage) -> Unit = {}
    var UserMessageWritten: (messageInfo:LongPoolUserMessage) -> Unit = {}
    var LongPoolResponsed: (updates:JsonArray<Any>) -> Unit = {}
    //-events

    init {
        println("Botlog:Api initialized")
    }

    fun apiReq(request: String, withoutCut:Boolean = false): JsonObject {
        println("Botlog:Requested=>${request}")
        val url = URL((if(!withoutCut)"https://api.vk.com/method/" else "") + request)
        val stream = url.openStream().bufferedReader()
        val buf = StringBuffer()
        var bufLine: String?
        do {
            bufLine = stream.readLine()
            buf.append(bufLine ?: "")
        } while (bufLine != null)

        val parser = Parser()
        val bufRes = StringBuilder()
        bufRes.append(buf.toString())
        val obj = parser.parse(bufRes) as JsonObject
        return obj
    }

    fun sendChatMessage(chat_id: Int? = null, message: String? = null): Int {
        val id = messagesSend(chat_id = chat_id, message = message)
        println("Message sent to chat $chat_id with id $id")
        return id[0]
    }

    fun sendUserMessage(user_ids: Array<Int> = arrayOf(), message: String? = null): Array<Int> {

        val id = messagesSend(user_ids = user_ids, message = message)
        println("Message sent to user $user_ids with id $id")
        return id
    }

    private fun messagesSend(user_ids: Array<Int> = arrayOf(), message: String? = null, chat_id: Int? = null):Array<Int> {
        println("Botlog:Sending=>$message")

        var ids = ""
        if(user_ids.count() > 1) {
            ids = user_ids.map { it.toString() }.joinToString { (it + ",") }
            if (ids[ids.length - 1] == ',')
                ids = ids.substring(0, ids.length - 1)
        }
        val req = apiReq("messages.send?" +
                "user_ids=$ids&" +
                "chat_id=${chat_id ?: ""}&" +
                "message=${URLEncoder.encode(message, "UTF-8")}&" +
                "&access_token=$token")
        when(user_ids.count()) {
            1 -> {
                 return arrayOf(req.int("response") ?: throw Exception("Message send error"))
            }
            2 -> {
                val items: ArrayList<Int> = ArrayList()
                (req.array<Int>("response") ?: throw Exception("Message send error")).forEach {
                    items.add(it)
                }
                return items.toTypedArray()
            }
            else -> return arrayOf(req.int("response")?:0)
        }
    }

    fun getServerTime():Date{
        return Date.from(Instant.ofEpochSecond(apiReq("utils.getServerTime").long("response")?:0))
    }

    val currentVKUser: VKUser = getUser()

    fun getUser(user_id:Int = -1): VKUser {
        val req = apiReq("users.get?${if(user_id == -1)"" else "user_ids=$user_id"}&fields=$userFields&access_token=$token")
        val res = req.array<JsonObject>("response")
        if(res?.count() == 0)
            throw Exception("VKUser not found")
        var user = VKUser(res!![0].string("first_name") ?: "Unknown",
                res[0].string("last_name") ?: "Unknown",
                res[0].int("uid") ?: 0,
                res[0].int("online")?:0 == 1,
                res[0].string("status")?:"Unknown")
        return user
    }

    fun reqLongPool():JsonObject {
        val poolReqRes = apiReq("messages.getLongPollServer?access_token=$token").obj("response")
        var res: JsonObject? = null
        res = apiReq("https://${poolReqRes?.string("server")}?act=a_check&key=${poolReqRes?.string("key")}&ts=${poolReqRes?.int("ts").toString()}&wait=25&mode=2&version=2", true)
        return res
    }

    fun listenLongPool() {
        isLPListening = true
        while(isLPListening)
        {
            val res = reqLongPool()
            if(res.array<Any>("updates")?.count() != 0)
            {
                res.array<JsonArray<Any>>("updates")?.forEach {
                    if(it[0] == 4) {
                        val peer:Int = it[3] as Int
                        if((peer>2000000000))
                        {
                            val msg = LongPoolChatMessage(it[5] as String, peer - 2000000000)
                            ChatMessageWritten(msg)
                        }
                        else
                        {
                            val msg = LongPoolUserMessage(it[5] as String, peer)
                            UserMessageWritten(msg)
                        }
                    }
                    LongPoolResponsed(it)
                }
            }
        }
    }
}
