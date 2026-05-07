package com.example.agenda

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AppointmentAlarmScheduler {
    private const val EXTRA_TITLE = "extra_title"
    private const val EXTRA_MESSAGE = "extra_message"
    private const val EXTRA_APPOINTMENT_ID = "extra_appointment_id"

    fun scheduleReminders(context: Context, appointment: Appointment) {
        cancelReminders(context, appointment)
        val now = System.currentTimeMillis()
        val reminders = listOf(
            ReminderData(appointment, appointment.appointmentDateTimeMs - 24 * 60 * 60 * 1000, "Lembrete: 1 dia antes"),
            ReminderData(appointment, appointment.appointmentDateTimeMs - 60 * 60 * 1000, "Lembrete: 1 hora antes")
        )

        for ((index, reminder) in reminders.withIndex()) {
            if (reminder.triggerAt > now) {
                scheduleAlarm(context, reminder, index)
            }
        }
    }

    fun cancelReminders(context: Context, appointment: Appointment) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (index in 0..1) {
            val intent = buildIntent(context, appointment, "")
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                getRequestCode(appointment.id, index),
                intent,
                getPendingIntentFlags(PendingIntent.FLAG_NO_CREATE)
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun scheduleAlarm(context: Context, reminder: ReminderData, index: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = buildIntent(context, reminder.appointment, reminder.message)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getRequestCode(reminder.appointment.id, index),
            intent,
            getPendingIntentFlags(0)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pendingIntent)
        }
    }

    private fun buildIntent(context: Context, appointment: Appointment, message: String): Intent {
        return Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TITLE, appointment.title)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_APPOINTMENT_ID, appointment.id)
        }
    }

    private fun getRequestCode(appointmentId: Long, index: Int): Int {
        val base = (appointmentId and 0x7FFFFFFF).toInt()
        return base * 10 + index
    }

    private fun getPendingIntentFlags(baseFlags: Int): Int {
        return baseFlags or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    private data class ReminderData(
        val appointment: Appointment,
        val triggerAt: Long,
        val message: String
    )
}
