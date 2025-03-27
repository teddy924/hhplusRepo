package io.hhplus.tdd.point;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/point")
public class PointController {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);

    private PointService pointService;

    @Autowired
    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    /**
     * 1. id를 param으로 받는다.
     * 2. pointService를 호출한다.
     * 3. UserPointTable에서 id를 기준으로 보유중인 포인트를 조회한다.
     * 4. 결과를 리턴한다.
     */
    @GetMapping("{id}")
    public UserPoint point(
            @PathVariable long id
    ) {

        UserPoint userPoint = new UserPoint(0, 0, 0);

        userPoint = pointService.retvPoint(id);

        return userPoint;
    }

    /**
     * 1. id를 param으로 받는다.
     * 2. pointService를 호출한다.
     * 3. PointHistoryTable에서 id를 기준으로 포인트 변경 이력을 조회한다.
     * 4. 조회된 포인트 이력을 시간 순으로 정렬한다.
     * 4. 결과를 리턴한다.
     */
    @GetMapping("{id}/histories")
    public List<PointHistory> history(
            @PathVariable long id
    ) {

        List<PointHistory> pointHistoryList = List.of();

        pointHistoryList = pointService.retvPointHistory(id);

        return pointHistoryList;
    }

    /**
     * 1. id와 amount를 param으로 받는다.
     * 2. pointService를 호출한다.
     * 3. UserPointTable에서 id를 기준으로 보유 중인 포인트를 조회한다.
     * 4. 조회한 보유중인 포인트에 전달받은 amount를 더한 후 포인트를 저장한다.
     * 5. PointHistoryTable에 id를 기준으로 충전 이력을 insert 한다.
     */
    @PatchMapping("{id}/charge")
    public UserPoint charge(
            @PathVariable long id,
            @RequestBody long amount
    ) throws Exception {

        UserPoint userPoint = new UserPoint(0, 0, 0);

        userPoint = pointService.chargePoint(id, amount);

        return userPoint;
    }

    /**
     * 1. id와 amount를 param으로 받는다.
     * 2. pointService를 호출한다.
     * 3. UserPointTable에서 id를 기준으로 보유 중인 포인트를 조회한다.
     * 4. 조회한 보유중인 포인트에 전달받은 amount를 차감한 후 포인트를 저장한다.
     * 5. PointHistoryTable에 id를 기준으로 사용 이력을 insert 한다.
     */
    @PatchMapping("{id}/use")
    public UserPoint use(
            @PathVariable long id,
            @RequestBody long amount
    ) throws Exception {

        UserPoint userPoint = new UserPoint(0, 0, 0);

        userPoint = pointService.usePoint(id, amount);

        return userPoint;
    }
}
