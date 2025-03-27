package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PointService {

    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;

    // 포인트 조회
    public UserPoint retvPoint(long id) {

        // 유효성 검사
        if (id == 0) {
            throw new IllegalArgumentException("userId는 0이 될 수 없습니다.");
        } else if (id < 0) {
            throw new IllegalArgumentException("userId는 음수가 될 수 없습니다.");
        }

        return userPointTable.selectById(id);
    }

    // 포인트 이력 조회
    public List<PointHistory> retvPointHistory(long id) {

        List<PointHistory> pointHistoryList = pointHistoryTable.selectAllByUserId(id);

        List<PointHistory> arrPointHistory = new ArrayList<>(pointHistoryList);
        // 조회한 포인트 이력 리스트를 입력된 시간 순으로 정렬
        arrPointHistory.sort(Comparator.comparing(PointHistory::updateMillis));

        return arrPointHistory;
    }

    // 포인트 충전
    public UserPoint chargePoint(long id, long amount) throws Exception {

        long onceChargeMaxPoint = 10000L;   // 1회 충전 최대 포인트양
        long canMaxPoint = 100000L;         // 보유가능 최대 포인트양
        long savePoint = 0L;                // DB 저장할 포인트양

        // 유효성 검사
        if (amount == 0) {
            throw new IllegalArgumentException("amount는 0이 될 수 없습니다.");
        } else if (amount < 0) {
            throw new IllegalArgumentException("amount는 음수가 될 수 없습니다.");
        }

        // 정책 : 1회 최대 충전 포인트 제한
        if (amount > onceChargeMaxPoint) {
            throw new IllegalArgumentException("1회 최대 충전 포인트는 1,000을 넘을 수 없습니다.");
        }

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

    }

    // 포인트 사용
    public UserPoint usePoint(long id, long amount) throws Exception {

        long onceUseMaxPoint = 10000L;      // 1회 사용 최대 포인트양
        long savePoint = 0L;                // DB 저장할 포인트양

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

        return new UserPoint(id, savePoint, System.currentTimeMillis());

    }
}
