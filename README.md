JAVA/SPRING - TDD - 백엔드 동시성 제어
===

동시성이란
---
#### 동시성 
- 동시성이란 애플리케이션이 둘 이상의 작업을 동시에 진행 중임을 의미한다.

#### Race Condition(레이스 컨디션)
- 둘 이상의 Thread가 공유 자원에 접근해서 동시에 변경을 할 때 발생하는 문제이다.

![image](https://github.com/user-attachments/assets/b906578d-ca16-4900-8eb6-6f237f601c95)

#### 동시성 제어
- 멀티스레드 환경에서 여러 스레드가 같은 자원에 접근할 때, 데이터 무결성을 보장하고, 경쟁 조건을 방지하는 기법이다.

동시성 제어의 종류
---
#### 1. Synchronized
- synchronized 키워드를 사용하여 메서드 또는 블록을 동기화.
- 한 번에 하나의 스레드만 접근 가능.

```
public synchronized void updatePoint(long userId, long amount) {
    // 포인트 업데이트 로직
}
```

#### 2. Lock
- synchronized보다 유연한 동기화 제어를 제공하는 명시적 Lock.
- lock()과 unlock()을 명시적으로 호출해야 함.
- tryLock()을 이용하면 락을 기다리지 않고 즉시 반환 가능.

```
private final ReentrantLock lock = new ReentrantLock();

public void updatePoint(long userId, long amount) {
    lock.lock();
    try {
        // 포인트 업데이트 로직
    } finally {
        lock.unlock();  // 반드시 unlock 필요!
    }
}
```

#### 3. Atomic 변수 (CAS - Compare And Swap)
- synchronized나 Lock 없이도 원자적으로 연산할 수 있는 변수.
- AtomicInteger, AtomicLong, AtomicReference 등을 사용.
- 내부적으로 CAS(Compare And Swap) 연산을 사용하여 동기화 문제를 해결.
```
private AtomicLong point = new AtomicLong(0);

public void addPoint(long amount) {
    point.addAndGet(amount);
}
```

#### 4. CompletableFuture (비동기 처리)
- 비동기적으로 작업을 수행하면서 동시성을 제어할 수 있음.
- 여러 작업을 병렬로 실행하고 결과를 조합 가능.
```
public CompletableFuture<UserPoint> getPointAsync(long userId) {
    return CompletableFuture.supplyAsync(() -> {
        return userPointTable.selectById(userId);
    });
}
```

#### 5. Database Lock (데이터베이스 수준 동기화)
- 비관적 락(Pessimistic Lock): SELECT ... FOR UPDATE 사용하여 트랜잭션 동안 다른 접근 차단.
- 낙관적 락(Optimistic Lock): @Version을 이용해 충돌 감지 후 재시도.
```
@Entity
public class UserPoint {
    @Id
    private Long userId;

    @Version
    private Long version;  // Optimistic Lock

    private Long point;
}
```

<hr/>
1-1. 참고 : https://ksh-coding.tistory.com/125
