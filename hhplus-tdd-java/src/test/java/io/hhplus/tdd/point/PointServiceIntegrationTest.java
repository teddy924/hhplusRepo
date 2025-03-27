package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class PointServiceIntegrationTest {

    @Autowired
    private PointService pointService; // 실제 서비스

    @Autowired
    private UserPointTable userPointTable; // 테스트 검증용

    @Autowired
    private PointHistoryTable pointHistoryTable; // 테스트 검증용

    @DisplayName("포인트 충전하고 조회하기")
    @Test
    void test_ChargeAndRetv() throws Exception {

        long userId = 1L;
        long initPoint = 3000L;
        long chargePoint = 5000L;

        userPointTable.insertOrUpdate(userId, initPoint);

        // 포인트 충전
        UserPoint aftCharge = pointService.chargePoint(userId, chargePoint);
        Assertions.assertThat(aftCharge.point()).isEqualTo(initPoint + chargePoint);

        // 포인트 조회
        UserPoint aftRetv = pointService.retvPoint(aftCharge.id());
        Assertions.assertThat(aftRetv.point()).isEqualTo(aftCharge.point());

    }

    @DisplayName("포인트 사용하고 조회하기")
    @Test
    void test_UseAndRetv() throws Exception {

        long userId = 2L;
        long initPoint = 90000L;
        long useAmount = 5000L;

        userPointTable.insertOrUpdate(userId, initPoint);

        // 포인트 사용
        UserPoint aftCharge = pointService.usePoint(userId, useAmount);
        Assertions.assertThat(aftCharge.point()).isEqualTo(initPoint - useAmount);

        // 포인트 조회
        UserPoint aftRetv = pointService.retvPoint(aftCharge.id());
        Assertions.assertThat(aftRetv.point()).isEqualTo(aftCharge.point());

    }

    @DisplayName("포인트 충전하고 사용 할 경우 포인트 적용이 잘 되었는지 ? ")
    @Test
    void test_ChargeAndUse() throws Exception {

        long userId = 1L;
        long initPoint = 4000L;
        long chargeAmount = 3000L;
        long useAmount = 1000L;

        userPointTable.insertOrUpdate(userId, initPoint);

        // 포인트 충전
        UserPoint aftCharge = pointService.chargePoint(userId, chargeAmount);
        Assertions.assertThat(aftCharge.point()).isEqualTo(initPoint + chargeAmount);

        // 포인트 사용
        UserPoint aftUse = pointService.usePoint(aftCharge.id(), useAmount);
        Assertions.assertThat(aftUse.point()).isEqualTo(aftCharge.point() - useAmount);

    }

    @DisplayName("보유 포인트 조회 후 보유량보다 많은 포인트를 사용하려할 경우 예외 발생하는지 ?")
    @Test
    void test_RetvAndUseOverPoint() throws Exception {
        long userId = 1L;
        long initPoint = 6000L;
        long useAmount = 9000L;

        userPointTable.insertOrUpdate(userId, initPoint);

        // 포인트 조회
        UserPoint aftRetv = pointService.retvPoint(userId);
        Assertions.assertThat(aftRetv.point()).isEqualTo(initPoint);

        // 포인트 사용
        Assertions.assertThatCode(() -> {pointService.usePoint(userId, useAmount);})
                .isInstanceOf(Exception.class)
                .hasMessageContaining("보유 포인트보다 많은 포인트");

    }

    @DisplayName("포인트 충전 후 이력 조회 시 이력이 잘 추가되었는지 ? ")
    @Test
    void test_ChargeAndRetvPointhistory() throws Exception {
        long userId = 1L;
        long chargeAmount = 3000L;
        long befPoint = userPointTable.selectById(userId).point();
        int befHistorySize = pointHistoryTable.selectAllByUserId(userId).size();
        int chargeCnt = 0;

        // 포인트 충전
        UserPoint aftCharge = pointService.chargePoint(userId, chargeAmount);
        Assertions.assertThat(aftCharge.point()).isEqualTo(befPoint + chargeAmount);
        chargeCnt++;

        // 포인트 이력 조회
        List<PointHistory> aftRetvHistory = pointService.retvPointHistory(userId);
        Assertions.assertThat(aftRetvHistory.size()).isEqualTo(befHistorySize + chargeCnt);

    }

    @DisplayName("포인트 사용 후 이력 조회 시 이력이 잘 추가되었는지 ? ")
    @Test
    void test_UseAndRetvPointhistory() throws Exception {
        long userId = 3L;
        long useAmount = 3000L;
        long setPoint = 30000L;
        userPointTable.insertOrUpdate(userId, setPoint);
        pointHistoryTable.insert(userId, setPoint, TransactionType.CHARGE, System.currentTimeMillis());
        long befPoint = userPointTable.selectById(userId).point();
        int befHistorySize = pointHistoryTable.selectAllByUserId(userId).size();
        int chargeCnt = 0;

        // 포인트 사용
        UserPoint aftCharge = pointService.usePoint(userId, useAmount);
        Assertions.assertThat(aftCharge.point()).isEqualTo(befPoint - useAmount);
        chargeCnt++;

        // 포인트 이력 조회
        List<PointHistory> aftRetvHistory = pointService.retvPointHistory(userId);
        Assertions.assertThat(aftRetvHistory.size()).isEqualTo(befHistorySize + chargeCnt);

    }

    @DisplayName("포인트 사용 - 동시성 테스트")
    @Test
    void test_UsePoint_Concurrency_CompletableFuture() throws Exception {
        // given
        long userId = 4L;
        long initPoint = 100_000L;
        long useAmount = 10_000L;
        int threadCount = 5;

        userPointTable.insertOrUpdate(userId, initPoint);

        // when
        List<CompletableFuture<UserPoint>> futures = IntStream.range(0, threadCount)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return pointService.usePoint(userId, useAmount);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }))
                .toList();

        // 모든 비동기 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // then
        UserPoint finalPoint = pointService.retvPoint(userId);
        Assertions.assertThat(finalPoint.point()).isEqualTo(initPoint - (useAmount * threadCount));
    }
}