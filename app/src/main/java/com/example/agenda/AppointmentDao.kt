package com.example.agenda

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE appointmentDateTimeMs >= :now ORDER BY appointmentDateTimeMs ASC")
    fun getUpcomingAppointments(now: Long = System.currentTimeMillis()): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE (title LIKE '%' || :search || '%' OR description LIKE '%' || :search || '%') AND appointmentDateTimeMs >= :now ORDER BY appointmentDateTimeMs ASC")
    fun searchAppointments(search: String, now: Long = System.currentTimeMillis()): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE category = :category AND appointmentDateTimeMs >= :now ORDER BY appointmentDateTimeMs ASC")
    fun getAppointmentsByCategory(category: String, now: Long = System.currentTimeMillis()): Flow<List<Appointment>>

    @Query("SELECT COUNT(*) FROM appointments WHERE appointmentDateTimeMs >= :now")
    fun getUpcomingCount(now: Long = System.currentTimeMillis()): Flow<Int>

    @Query("SELECT * FROM appointments WHERE appointmentDateTimeMs >= :now ORDER BY appointmentDateTimeMs ASC LIMIT 1")
    fun getNextAppointment(now: Long = System.currentTimeMillis()): Flow<Appointment?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appointment: Appointment): Long

    @Update
    suspend fun update(appointment: Appointment)

    @Delete
    suspend fun delete(appointment: Appointment)

    @Query("DELETE FROM appointments WHERE appointmentDateTimeMs < :now")
    suspend fun deleteExpired(now: Long)
}
