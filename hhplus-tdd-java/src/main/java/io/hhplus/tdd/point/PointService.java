package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PointService {

    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;

    private final ExecutorService executor = Executors.newFixedThreadPool(10); // 비동기 작업을 위한 스레드 풀
    private final Map<Long, Lock> userLocks = new ConcurrentHashMap<>();                // 사용자별 락 관리
    private final Map<Long, Integer> requestCountMap = new ConcurrentHashMap<>();       // 각 사용자의 요청 수를 관리

    // 포인트 조회
    public UserPoint retvPoint(long userId) {

        // 유효성 검사
        if (userId == 0) {
            throw new IllegalArgumentException("userId는 0이 될 수 없습니다.");
        } else if (userId < 0) {
            throw new IllegalArgumentException("userId는 음수가 될 수 없습니다.");
        }

        // 락을 사용자별로 관리
        Lock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());    // 사용자별 락
        lock.lock();                                                                      // 락을 획득하여 다른 요청들이 대기하도록 처리

        try {
            // 현재 요청 수 가져오기
            int requestCount = requestCountMap.getOrDefault(userId, 0);

            // 동일 사용자가 보낸 요청이 n개 미만인 경우
            int futureCnt = 5;
            if (requestCount < futureCnt) {
                requestCountMap.put(userId, requestCount + 1); // 요청 수 증가
            }

            // 비동기로 포인트 정보 조회
            CompletableFuture<UserPoint> future = CompletableFuture.supplyAsync(() -> {
                return userPointTable.selectById(userId);
            }, executor);

            // 비동기 결과가 완료되었으면 UserPoint 반환
            return future.get();

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("유저 포인트 조회 중 오류 발생", e);
        } finally {
            lock.unlock(); // 작업이 끝나면 락을 해제하여 다른 요청이 들어올 수 있게 함
        }
    }

    // 포인트 이력 조회
    public List<PointHistory> retvPointHistory(long id) {

        // 락을 사용자별로 관리
        Lock lock = userLocks.computeIfAbsent(id, k -> new ReentrantLock()); // 사용자별 락
        lock.lock();                                                               // 락을 획득하여 다른 요청들이 대기하도록 처리

        try {
            // 현재 요청 수 가져오기
            int requestCount = requestCountMap.getOrDefault(id, 0);

            // 동일 사용자가 보낸 요청이 n개 미만인 경우
            int cdLatchCnt = 5;
            if (requestCount < cdLatchCnt) {
                requestCountMap.put(id, requestCount + 1); // 요청 수 증가
            }

            // 비동기로 포인트 이력 조회
            CompletableFuture<List<PointHistory>> future = CompletableFuture.supplyAsync(() -> {
                List<PointHistory> pointHistoryList = pointHistoryTable.selectAllByUserId(id);
                List<PointHistory> arrPointHistory = new ArrayList<>(pointHistoryList);
                arrPointHistory.sort(Comparator.comparing(PointHistory::updateMillis));
                return arrPointHistory;
            }, executor);

            // 비동기 결과가 완료되었으면 포인트 이력 반환
            return future.get();

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("포인트 이력 조회 중 오류 발생", e);
        } finally {
            lock.unlock(); // 작업이 끝나면 락을 해제하여 다른 요청이 들어올 수 있게 함
        }
    }

    public UserPoint chargePoint(long id, long amount) throws Exception {
        long onceChargeMaxPoint = 10000L;   // 1회 충전 최대 포인트양
        long canMaxPoint = 100000L;         // 보유가능 최대 포인트양

        // 유효성 검사
        if (amount == 0) {
            throw new IllegalArgumentException("amount는 0이 될 수 없습니다.");
        } else if (amount < 0) {
            throw new IllegalArgumentException("amount는 음수가 될 수 없습니다.");
        }

        // 정책 : 1회 최대 충전 포인트 제한
        if (amount > onceChargeMaxPoint) {
            throw new IllegalArgumentException("1회 최대 충전 포인트는 10,000을 넘을 수 없습니다.");
        }

        // 락을 사용자별로 관리
        Lock lock = userLocks.computeIfAbsent(id, k -> new ReentrantLock()); // 사용자별 락
        lock.lock();                                                               // 락을 획득하여 다른 요청들이 대기하도록 처리

        try {
            // 현재 요청 수 가져오기
            int requestCount = requestCountMap.getOrDefault(id, 0);

            // 동일 사용자가 보낸 요청이 n개 미만인 경우
            int cdLatchCnt = 5;
            if (requestCount < cdLatchCnt) {
                requestCountMap.put(id, requestCount + 1); // 요청 수 증가
            }

            CompletableFuture<UserPoint> future = CompletableFuture.supplyAsync(() -> {
                long savePoint = 0L;                // DB 저장할 포인트양

                // 보유 포인트 조회
                UserPoint userPoint = userPointTable.selectById(id);
                savePoint = userPoint.point() + amount;

                // 정책 : 보유 가능한 최대 포인트 제한
                if (savePoint > canMaxPoint) {
                    throw new IllegalArgumentException("보유 가능한 최대 포인트는 100,000을 넘을 수 없습니다.");
                }

                // 포인트 갱신
                userPointTable.insertOrUpdate(id, savePoint);
                // 포인트 이력 추가
                pointHistoryTable.insert(id, amount, TransactionType.CHARGE, System.currentTimeMillis());

                return new UserPoint(id, savePoint, System.currentTimeMillis());
            }, executor);

            // 비동기 결과가 완료되었으면 포인트 반환
            return future.get();

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause();                                // 실제 예외를 추적
            if (cause instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) cause;                    // 원래 예외 던지기
            } else {
                Thread.currentThread().interrupt();
                throw new RuntimeException("포인트 충전 중 오류 발생", cause);  // 다른 예외는 RuntimeException으로 처리
            }
        } finally {
            lock.unlock(); // 작업이 끝나면 락을 해제하여 다른 요청이 들어올 수 있게 함
        }
    }

    public UserPoint usePoint(long id, long amount) throws Exception {

        long onceUseMaxPoint = 10000L;      // 1회 사용 최대 포인트양

        // 유효성 검사
        if (amount == 0) {
            throw new IllegalArgumentException("amount는 0이 될 수 없습니다.");
        } else if (amount < 0) {
            throw new IllegalArgumentException("amount는 음수가 될 수 없습니다.");
        }

        // 정책 : 1회 최대 사용 포인트 제한
        if (amount > onceUseMaxPoint) {
            throw new IllegalArgumentException("1회 최대 사용 포인트는 10,000을 넘을 수 없습니다.");
        }

        // 락을 사용자별로 관리
        Lock lock = userLocks.computeIfAbsent(id, k -> new ReentrantLock()); // 사용자별 락
        lock.lock();  // 락을 획득하여 다른 요청들이 대기하도록 처리

        try {
            // 현재 요청 수 가져오기
            int requestCount = requestCountMap.getOrDefault(id, 0);

            // 동일 사용자가 보낸 요청이 n개 미만인 경우
            int cdLatchCnt = 5;
            if (requestCount < cdLatchCnt) {
                requestCountMap.put(id, requestCount + 1); // 요청 수 증가
            }

            // 비동기로 포인트 사용 처리
            CompletableFuture<UserPoint> future = CompletableFuture.supplyAsync(() -> {
                long savePoint = 0L;                // DB 저장할 포인트양

                // 보유 포인트 조회
                UserPoint userPoint = userPointTable.selectById(id);
                savePoint = userPoint.point() - amount;

                // 정책 : 보유 포인트보다 많은 포인트 사용 제한
                if (savePoint < 0) {
                    throw new IllegalArgumentException("보유 포인트보다 많은 포인트를 사용할 수 없습니다.");
                }

                // 포인트 갱신
                userPointTable.insertOrUpdate(id, savePoint);
                // 포인트 이력 추가
                pointHistoryTable.insert(id, amount, TransactionType.USE, System.currentTimeMillis());

                // 갱신된 포인트 반환
                return new UserPoint(id, savePoint, System.currentTimeMillis());
            }, executor);

            // 비동기 결과가 완료되었으면 포인트 반환
            return future.get();

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause();  // 실제 예외를 추적
            if (cause instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) cause;  // 원래 예외 던지기
            } else {
                Thread.currentThread().interrupt();
                throw new RuntimeException("포인트 사용 중 오류 발생", cause);  // 다른 예외는 RuntimeException으로 처리
            }
        } finally {
            lock.unlock(); // 작업이 끝나면 락을 해제하여 다른 요청이 들어올 수 있게 함
        }
    }
}
