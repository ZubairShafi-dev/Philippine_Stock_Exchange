package com.pse.pse.data.repository.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.pse.pse.utils.SharedPrefManager
import com.pse.pse.models.chat.Admin
import com.pse.pse.models.chat.ChatPreview
import com.pse.pse.models.chat.Message

class ChatRepository(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val sharedPrefManager = SharedPrefManager(context)
    private val chatPreviewList: MutableLiveData<List<ChatPreview>> = MutableLiveData()
    private var chatListenerRegistration: ListenerRegistration? = null

    val admin: MutableLiveData<List<Admin>> = MutableLiveData()

    fun fetchAdminList() {
        firestore.collection("Admin").addSnapshotListener { querySnapshot, error ->
            if (querySnapshot != null) {
                val admins = mutableListOf<Admin>()
                for (document in querySnapshot.documents) {
                    val admin = document.toObject(Admin::class.java)
                    if (admin != null) {
                        admin.id = document.id
                        admins.add(admin)
                    }
                }
                admin.value = admins
                Log.d("ChatRepository", "✅ Admins loaded: ${admins.map { it.id }}")
            } else {
                Log.e("ChatRepository", "❌ Error fetching admin list: ${error?.message}")
            }
        }
    }

    fun getAdmin(): LiveData<List<Admin>> {
        fetchAdminList()
        return admin
    }

    fun sendMessage(userId: String?, messageText: String?, adminId: String?) {
        val message = Message(
            id = "",
            message = messageText ?: "",
            senderId = userId ?: "",
            receiverId = adminId ?: "",
            status = Message.STATUS_SENT,
            sender = "1" // user sending
        )

        firestore.collection("chats").add(message).addOnSuccessListener { documentReference ->
            val id = documentReference.id
            firestore.collection("chats").document(id).update("id", id)
            Log.d("MessageRepository", "Message sent successfully")
        }.addOnFailureListener { e ->
            Log.e("MessageRepository", "Error sending message", e)
        }
    }

    fun getMessages(userId: String?, messagesLiveData: MutableLiveData<List<Message>>) {
        firestore.collection("chats").orderBy("createdAt")
            .addSnapshotListener { snapshot: QuerySnapshot?, e: FirebaseFirestoreException? ->
                if (e != null) {
                    Log.e("ChatRepository", "Error fetching messages: ", e)
                    messagesLiveData.postValue(emptyList())
                    return@addSnapshotListener
                }

                val messageList: MutableList<Message> = mutableListOf()
                snapshot?.documents?.forEach { document ->
                    val message = document.toObject(Message::class.java)
                    if (message != null) {
                        if (message.senderId == userId || message.receiverId == userId) {
                            messageList.add(message)
                        }
                    }
                }

                messageList.sortBy { it.createdAt?.toDate() }
                messagesLiveData.postValue(messageList)
            }
    }

    fun getChats(userId: String?, adminId: String): LiveData<List<Message>> {
        val chats: MutableLiveData<List<Message>> = MutableLiveData()
        val chatList = mutableListOf<Message>()
        val messageIds = mutableSetOf<String>()

        firestore.collection("chats")
            .whereIn("senderId", listOf(userId, adminId))
            .whereIn("receiverId", listOf(userId, adminId))
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { querySnapshot, error ->
                if (error != null) return@addSnapshotListener

                chatList.clear()
                messageIds.clear()

                querySnapshot?.documents?.forEach { document ->
                    val message = document.toObject(Message::class.java)
                    message?.let {
                        if (!messageIds.contains(it.id)) {
                            chatList.add(it)
                            messageIds.add(it.id)
                        }
                    }
                }

                chatList.sortBy { it.createdAt?.toDate() }
                chats.postValue(chatList)
            }

        return chats
    }

    fun getChatPreviewList(): LiveData<List<ChatPreview>> {
        fetchChatPreviewList()
        return chatPreviewList
    }

    fun fetchChatPreviewList() {
        chatListenerRegistration?.remove()
        val currentUserId = sharedPrefManager.getId() ?: return
        Log.d("ChatRepo", "👤 Current userId: $currentUserId")

        chatListenerRegistration =
            firestore.collection("chats").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatPreview", "Error fetching chats", error)
                    return@addSnapshotListener
                }

                val chatPreviews = mutableMapOf<String, ChatPreview>()

                snapshot?.documents?.forEach { document ->
                    val senderId = document.getString("senderId") ?: return@forEach
                    val receiverId = document.getString("receiverId") ?: return@forEach
                    val message = document.getString("message") ?: "No Message"
                    val timestamp = (document["createdAt"] as? Timestamp)?.toDate()?.time
                        ?: return@forEach

                    val otherId = when (currentUserId) {
                        senderId -> receiverId
                        receiverId -> senderId
                        else -> return@forEach
                    }

                    val existingChat = chatPreviews[otherId]
                    if (existingChat == null || timestamp > existingChat.timestamp) {
                        chatPreviews[otherId] =
                            ChatPreview(otherId, "Fetching...", message, timestamp)
                    }
                }

                fetchAdminNames(chatPreviews)
            }
    }

    private fun fetchAdminNames(chatPreviews: MutableMap<String, ChatPreview>) {
        val adminIds = chatPreviews.keys.toList()

        if (adminIds.isEmpty()) {
            chatPreviewList.value = emptyList()
            return
        }

        firestore.collection("Admin").whereIn("id", adminIds).get()
            .addOnSuccessListener { adminSnapshot ->
                adminSnapshot.documents.forEach { doc ->
                    val adminId = doc.getString("id") ?: return@forEach
                    val adminName = doc.getString("name") ?: "Unknown"

                    chatPreviews[adminId]?.let {
                        chatPreviews[adminId] = it.copy(userName = adminName)
                    }
                }
                chatPreviewList.value = chatPreviews.values.sortedByDescending { it.timestamp }
            }
            .addOnFailureListener { e ->
                Log.e("ChatRepository", "❌ Failed to fetch admin names", e)
            }
    }
}
