package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceUnitTest {

    @InjectMocks
    PointService pointService;

    @Mock
    PointHistoryTable pointHistoryTable;

    @Mock
    UserPointTable userPointTable;

    // ===== retvPoint (포인트 조회) ====
    @DisplayName("userId값이 0일 때 예외 발생하는지 ? ")
    @Test
    void isException_UserId_Value_Zero() {
        //given
        long userId = 0;

        //when - then
        Assertions.assertThatThrownBy(() -> { pointService.retvPoint(userId);})
                .isInstanceOf(Exception.class)
                .hasMessageContaining("0이 될 수 없습니다.");

    }

    @DisplayName("userId값이 음수일 때 예외 발생하는지 ? ")
    @Test
    void isException_UserId_Value_Minus() {
        //given
        long userId = -1L;

        //when - then
        Assertions.assertThatThrownBy(() -> { pointService.retvPoint(userId);})
                .isInstanceOf(Exception.class)
                .hasMessageContaining("음수가 될 수 없습니다.");

    }

    // ===== retvPointHistory (포인트 이력 조회) =====
    @DisplayName("포인트 이력 조회 결과의 정렬 기준이 포인트양이 아닌지 ? ")
    @Test
    void isSorted_PointHistory_ByAmount() {
        //given
        long userId = 1L;
        long cursor = 1;
        List<PointHistory> mockPointHistoryList = List.of(
                new PointHistory(cursor++, userId, 5000L, TransactionType.CHARGE, 1000L)
                , new PointHistory(cursor++, userId,1000L, TransactionType.USE, 1000L)
                , new PointHistory(cursor++, userId, 9000L, TransactionType.CHARGE, 1000L)
                , new PointHistory(cursor++, userId,6000L, TransactionType.USE, 1000L)
                , new PointHistory(cursor++, userId,3000L, TransactionType.USE, 1000L)
        );

        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(mockPointHistoryList);

        //when
        List<PointHistory> res = pointService.retvPointHistory(userId);

        //then
        List<PointHistory> sortedMockList = new ArrayList<>(mockPointHistoryList);
        sortedMockList.sort(Comparator.comparing(PointHistory::amount));
        Assertions.assertThat(res).isNotEqualTo(sortedMockList);

    }

    @DisplayName("포인트 이력 조회 결과의 정렬 기준이 입력 시간인지 ? ")
    @Test
    void isSorted_PointHistory_ByIndex() {
        //given
        long userId = 1L;
        long cursor = 1;

        List<PointHistory> mockPointHistoryList = List.of(
                new PointHistory(cursor++, userId, 5000L, TransactionType.CHARGE, System.currentTimeMillis())
                , new PointHistory(cursor++, userId,1000L, TransactionType.USE, System.currentTimeMillis())
                , new PointHistory(cursor++, userId, 3000L, TransactionType.CHARGE, System.currentTimeMillis())
                , new PointHistory(cursor++, userId,6000L, TransactionType.USE, System.currentTimeMillis())
                , new PointHistory(cursor++, userId,1000L, TransactionType.USE, System.currentTimeMillis())
        );

        when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(mockPointHistoryList);

        //when
        List<PointHistory> res = pointService.retvPointHistory(userId);

        //then
        Assertions.assertThat(res)
                .isSortedAccordingTo(Comparator.comparing(PointHistory::updateMillis));
    }

    // ===== chargePoint (포인트 충전) =====
    @DisplayName("충전하려는 포인트가 0이면 예외 발생하는지?")
    @Test
    void isException_ChargeAmount_Value_Zero() throws Exception {
        //given
        long userId = 1L;
        long amount = 0;

        //when - then
        Assertions.assertThatThrownBy(() -> { pointService.chargePoint(userId, amount);})
                .isInstanceOf(Exception.class)
                .hasMessageContaining("0이 될 수 없습니다.");
    }

    @DisplayName("충전하려는 포인트가 음수이면 예외 발생하는지?")
    @Test
    void isException_ChargeAmount_Value_Minus() throws Exception {
        //given
        long userId = 1L;
        long amount = -10L;

        //when - then
        Assertions.assertThatThrownBy(() -> { pointService.chargePoint(userId, amount);})
                .isInstanceOf(Exception.class)
                .hasMessageContaining("음수가 될 수 없습니다.");
    }

    @DisplayName("충전 포인트만큼만 충전되는지 ? ")
    @Test
    void isTrue_ChargePoint_To_Save() throws Exception {
        //given
        long id = 1L;
        long amount = 3000L;

        UserPoint mockUserPoint = new UserPoint(id, 0L, System.currentTimeMillis());

        when(userPointTable.selectById(id)).thenReturn(mockUserPoint);

        //when
        UserPoint res = pointService.chargePoint(id, amount);

        //then
        Assertions.assertThat(res.point()).isEqualTo(mockUserPoint.point() + amount);
    }

    @DisplayName("한번에 충전하는 포인트가 10,000을 넘으면 예외가 발생하는지 ? ")
    @Test
    void isException_OnceChargePoint_10000_Over() {
        //given
        long id = 1L;
        long amount = 30000L;

        //when - then
        Assertions.assertThatThrownBy(() -> { pointService.chargePoint(id, amount);})
                .isInstanceOf(Exception.class)
                .hasMessageContaining("1회 최대 충전");

    }

    @DisplayName("한번에 충전하려는 포인트가 10,000을 안 넘으면 예외 발생 안 하는지 ? ")
    @Test
    void isTrue_OnceChargePoint_10000_Under() {
        //given
        long id = 1L;
        long amount = 7000L;

        UserPoint mockUserPoint = new UserPoint(id, 9000L, System.currentTimeMillis());

        when(userPointTable.selectById(id)).thenReturn(mockUserPoint);

        //when - then
        Assertions.assertThatCode(() -> { pointService.chargePoint(id, amount);})
                .doesNotThrowAnyException();

    }

    @DisplayName("포인트 충전 후 보유 포인트가 100,000을 넘으면 예외 발생하는지 ? ")
    @Test
    void isException_MaxPoint_100000_Over() {
        //given
        long id = 1L;
        long amount = 9000L;

        UserPoint mockUserPoint = new UserPoint(id, 99000L, System.currentTimeMillis());

        when(userPointTable.selectById(id)).thenReturn(mockUserPoint);

        //when - then
        Assertions.assertThatThrownBy(() -> { pointService.chargePoint(id, amount);})
                .isInstanceOf(Exception.class)
                .hasMessageContaining("보유 가능한 최대 포인트");

    }

    @DisplayName("포인트 충전 후 보유 포인트가 100,000을 안 넘으면 예외 발생 안 하는지 ? ")
    @Test
    void isTrue_MaxPoint_100000_Under() {
        //given
        long id = 1L;
        long amount = 7000L;

        UserPoint mockUserPoint = new UserPoint(id, 10000L, System.currentTimeMillis());

        when(userPointTable.selectById(id)).thenReturn(mockUserPoint);

        //when - then
        Assertions.assertThatCode(() -> { pointService.chargePoint(id, amount);})
                .doesNotThrowAnyException();

    }

    // ==== usePoint (포인트 사용) ====
    @DisplayName("사용하려는 포인트가 0이면 예외 발생하는지?")
    @Test
    void isException_UseAmount_Value_Zero() throws Exception {
        //given
        long userId = 1L;
        long amount = 0;

        //when - then
        Assertions.assertThatThrownBy(() -> { pointService.usePoint(userId, amount);})
                .isInstanceOf(Exception.class)
                .hasMessageContaining("0이 될 수 없습니다.");
    }

    @DisplayName("사용하려는 포인트가 음수이면 예외 발생하는지?")
    @Test
    void isException_UseAmount_Value_Minus() throws Exception {
        //given
        long userId = 1L;
        long amount = -10L;

        //when - then
        Assertions.assertThatThrownBy(() -> { pointService.usePoint(userId, amount);})
                .isInstanceOf(Exception.class)
                .hasMessageContaining("음수가 될 수 없습니다.");
    }

    @DisplayName("사용 포인트만큼만 사용되는지 ? ")
    @Test
    void isTrue_UsePoint_To_Save() throws Exception {
        //given
        long id = 1L;
        long amount = 3000L;

        UserPoint mockUserPoint = new UserPoint(id, 4000L, System.currentTimeMillis());

        when(userPointTable.selectById(id)).thenReturn(mockUserPoint);

        //when
        UserPoint res = pointService.usePoint(id, amount);

        //then
        Assertions.assertThat(res.point()).isEqualTo(mockUserPoint.point() - amount);
    }

    @DisplayName("한번에 사용하려는 포인트가 10,000을 넘으면 예외 발생하는지 ? ")
    @Test
    void isException_OnceUsePoint_10000_Over() {
        //given
        long id = 1L;
        long amount = 20000L;

        //when - then
        Assertions.assertThatThrownBy(() -> { pointService.usePoint(id, amount);})
                .isInstanceOf(Exception.class)
                .hasMessageContaining("1회 최대 사용");

    }

    @DisplayName("한번에 사용하려는 포인트가 10,000을 안 넘으면 예외 발생 안 하는지 ? ")
    @Test
    void isTrue_OnceUsePoint_10000_Under() {
        //given
        long id = 1L;
        long amount = 2000L;

        UserPoint mockUserPoint = new UserPoint(id, 10000L, System.currentTimeMillis());

        when(userPointTable.selectById(id)).thenReturn(mockUserPoint);

        //when - then
        Assertions.assertThatCode(() -> { pointService.usePoint(id, amount);})
                .doesNotThrowAnyException();
    }

    @DisplayName("보유한 포인트보다 사용할 포인트가 클 경우 예외 발생하는지 ? ")
    @Test
    void isException_UserPoint_BiggerThen_UsePoint() {
        //given
        long id = 1L;
        long amount = 2000L;

        UserPoint mockUserPoint = new UserPoint(id, 1000L, System.currentTimeMillis());

        when(userPointTable.selectById(id)).thenReturn(mockUserPoint);

        //when - then
        Assertions.assertThatThrownBy(() -> { pointService.usePoint(id, amount);})
                .isInstanceOf(Exception.class)
                .hasMessageContaining("보유 포인트보다 많은 포인트");

    }

}