package com.pse.pse.data.local.Dao

import UserModel
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserModel)

    @Query("SELECT * FROM users WHERE uid = :uid")
    suspend fun getUser(uid: String): UserModel?

    @Query("DELETE FROM users")
    suspend fun clearUsers()
}
