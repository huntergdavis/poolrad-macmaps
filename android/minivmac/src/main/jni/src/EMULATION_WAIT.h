/* Event-driven host wait. Take a ticket BEFORE inspecting pending work:
 * a request arriving between inspection and sleep must not be lost.
 * No timeout: an idle emulator does no periodic work. */
#ifndef EMULATION_WAIT_H
#define EMULATION_WAIT_H
#include <pthread.h>
#include <stdint.h>

typedef struct {
    pthread_mutex_t mutex;
    pthread_cond_t condition;
    uint64_t generation;
} emulation_wait;
#define EMULATION_WAIT_INITIALIZER { PTHREAD_MUTEX_INITIALIZER, PTHREAD_COND_INITIALIZER, 0 }

static uint64_t emulation_wait_ticket(emulation_wait *wait)
{
    uint64_t ticket;
    pthread_mutex_lock(&wait->mutex);
    ticket = wait->generation;
    pthread_mutex_unlock(&wait->mutex);
    return ticket;
}

static void emulation_wait_wake(emulation_wait *wait)
{
    pthread_mutex_lock(&wait->mutex);
    ++wait->generation;
    pthread_cond_signal(&wait->condition);
    pthread_mutex_unlock(&wait->mutex);
}

static void emulation_wait_until_changed(emulation_wait *wait, uint64_t ticket)
{
    pthread_mutex_lock(&wait->mutex);
    while (ticket == wait->generation)
        pthread_cond_wait(&wait->condition, &wait->mutex);
    pthread_mutex_unlock(&wait->mutex);
}
#endif
