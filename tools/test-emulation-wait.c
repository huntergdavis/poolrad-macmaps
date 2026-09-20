#define _POSIX_C_SOURCE 200809L
#include <assert.h>
#include <stdatomic.h>
#include <stdio.h>
#include <time.h>
#include <unistd.h>
#include "../android/minivmac/src/main/jni/src/EMULATION_WAIT.h"

static emulation_wait gate = EMULATION_WAIT_INITIALIZER;
static atomic_int finished;
static uint64_t ticket;

static void *sleeper(void *unused)
{
    (void)unused;
    emulation_wait_until_changed(&gate, ticket);
    atomic_store(&finished, 1);
    return NULL;
}

static void delay(void)
{
    struct timespec time = {0, 20000000};
    nanosleep(&time, NULL);
}

static long long nanoseconds(struct timespec time)
{
    return (long long)time.tv_sec * 1000000000 + time.tv_nsec;
}

int main(void)
{
    alarm(20); /* A lost wake-up fails, rather than hanging CI. */
    ticket = emulation_wait_ticket(&gate);
    emulation_wait_wake(&gate);
    emulation_wait_until_changed(&gate, ticket); /* wake before wait */

    pthread_t thread;
    clockid_t clock;
    struct timespec before, after;
    ticket = emulation_wait_ticket(&gate);
    assert(pthread_create(&thread, NULL, sleeper, NULL) == 0);
    assert(pthread_getcpuclockid(thread, &clock) == 0);
    delay();
    clock_gettime(clock, &before);
    /* A spurious signal must not escape or start periodic work. */
    pthread_mutex_lock(&gate.mutex);
    pthread_cond_signal(&gate.condition);
    pthread_mutex_unlock(&gate.mutex);
    for (int i = 0; i < 10; ++i) delay();
    clock_gettime(clock, &after);
    assert(!atomic_load(&finished));
    assert(nanoseconds(after) - nanoseconds(before) < 5000000);
    emulation_wait_wake(&gate); /* wake while blocked */
    assert(pthread_join(thread, NULL) == 0);
    assert(atomic_load(&finished));

    /* Race publication against entry into the wait repeatedly. */
    for (int i = 0; i < 2000; ++i) {
        atomic_store(&finished, 0);
        ticket = emulation_wait_ticket(&gate);
        assert(pthread_create(&thread, NULL, sleeper, NULL) == 0);
        emulation_wait_wake(&gate);
        assert(pthread_join(thread, NULL) == 0);
        assert(atomic_load(&finished));
    }
    puts("PASS: pre-wait/blocked wake, spurious signal, idle CPU, 2000 wake races");
}
