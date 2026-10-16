package com.amilingo.platform.module.lang.service;

import com.amilingo.platform.module.lang.dto.request.ScheduleRequest;
import com.amilingo.platform.module.lang.dto.response.ScheduleView;
import com.amilingo.platform.module.lang.entity.ScheduleBlock;
import com.amilingo.platform.module.lang.repository.ScheduleBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 时间资源服务：用户标记的独立时间段（24 小时分钟级），含重叠冲突校验。 */
@Service
public class ScheduleService {

    /** 24 小时全天分钟数（时间资源模块不再限制学习窗口）。 */
    private static final int FULL_DAY_MINUTES = 24 * 60;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ScheduleBlockRepository scheduleBlockRepository;

    public ScheduleService(ScheduleBlockRepository scheduleBlockRepository) {
        this.scheduleBlockRepository = scheduleBlockRepository;
    }

    @Transactional
    public ScheduleView saveBlocks(Long userId, ScheduleRequest request) {
        List<ScheduleBlock> toSave = new ArrayList<>();
        if (request.blocks() != null) {
            for (ScheduleRequest.BlockInput input : request.blocks()) {
                ScheduleBlock block = buildBlock(userId, input);
                checkOverlap(block, toSave);
                toSave.add(block);
            }
        }
        scheduleBlockRepository.deleteByUserId(userId);
        toSave.forEach(scheduleBlockRepository::save);
        return getSchedule(userId);
    }

    @Transactional(readOnly = true)
    public ScheduleView getSchedule(Long userId) {
        List<ScheduleBlock> all = scheduleBlockRepository.findByUserId(userId);

        List<ScheduleView.DayView> days = new ArrayList<>();
        int totalOccupied = 0;
        int totalFree = 0;
        for (int day = 1; day <= 7; day++) {
            int d = day;
            List<ScheduleBlock> blocks = all.stream()
                    .filter(b -> b.getDayOfWeek() == d)
                    .sorted(Comparator.comparing(ScheduleBlock::getStartTime))
                    .toList();

            int occupied = blocks.stream()
                    .filter(b -> b.getBlockType() == ScheduleBlock.BlockType.UNAVAILABLE
                            || b.getBlockType() == ScheduleBlock.BlockType.FRAGMENT
                            || b.getBlockType() == ScheduleBlock.BlockType.REST)
                    .mapToInt(b -> minutesBetween(b.getStartTime(), b.getEndTime()))
                    .sum();
            int free = Math.max(0, FULL_DAY_MINUTES - occupied);

            List<ScheduleView.BlockView> blockViews = blocks.stream()
                    .map(b -> new ScheduleView.BlockView(
                            b.getId(),
                            b.getStartTime().format(TIME_FMT),
                            b.getEndTime().format(TIME_FMT),
                            b.getBlockType() != null ? b.getBlockType().name() : "FREE",
                            b.getTitle(),
                            b.getRecurrence() != null ? b.getRecurrence().name() : "WEEKLY",
                            b.getEffectiveFrom() != null ? b.getEffectiveFrom().toString() : null,
                            b.getEffectiveTo() != null ? b.getEffectiveTo().toString() : null,
                            b.getSpecificDate() != null ? b.getSpecificDate().toString() : null))
                    .toList();

            days.add(new ScheduleView.DayView(day, blockViews, occupied, free));
            totalOccupied += occupied;
            totalFree += free;
        }
        return new ScheduleView(days, totalOccupied, totalFree);
    }

    /** 构建实体并做基础校验 + 同一天内重叠冲突校验。 */
    private ScheduleBlock buildBlock(Long userId, ScheduleRequest.BlockInput input) {
        if (input.dayOfWeek() == null || input.dayOfWeek() < 1 || input.dayOfWeek() > 7) {
            throw new IllegalArgumentException("dayOfWeek 必须在 1-7");
        }
        if (input.startTime() == null || input.endTime() == null) {
            throw new IllegalArgumentException("起止时间不能为空");
        }
        LocalTime s = parseTime(input.startTime());
        LocalTime e = parseTime(input.endTime());
        if (!e.isAfter(s)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }

        ScheduleBlock.RecurrenceType recurrence = parseRecurrence(input.recurrence());
        LocalDate specificDate = parseDate(input.specificDate());
        if (recurrence == ScheduleBlock.RecurrenceType.ONCE && specificDate == null) {
            throw new IllegalArgumentException("一次性事件必须填写 specificDate");
        }

        return ScheduleBlock.builder()
                .userId(userId)
                .dayOfWeek(input.dayOfWeek())
                .startTime(s)
                .endTime(e)
                .blockType(parseType(input.blockType()))
                .title(input.title())
                .recurrence(recurrence)
                .effectiveFrom(parseDate(input.effectiveFrom()))
                .effectiveTo(parseDate(input.effectiveTo()))
                .specificDate(specificDate)
                .build();
    }

    /** 同一天（或同一具体日期）内，新块与已有块重叠则抛冲突。 */
    private void checkOverlap(ScheduleBlock candidate, List<ScheduleBlock> existing) {
        for (ScheduleBlock b : existing) {
            boolean bOnce = b.getRecurrence() == ScheduleBlock.RecurrenceType.ONCE;
            boolean cOnce = candidate.getRecurrence() == ScheduleBlock.RecurrenceType.ONCE;
            // 两个一次性块：只和同一具体日期的比；其余情况按 dayOfWeek 比
            if (bOnce && cOnce) {
                if (!b.getSpecificDate().equals(candidate.getSpecificDate())) continue;
            } else if (!b.getDayOfWeek().equals(candidate.getDayOfWeek())) {
                continue;
            }
            if (candidate.getStartTime().isBefore(b.getEndTime()) && candidate.getEndTime().isAfter(b.getStartTime())) {
                throw new IllegalArgumentException(String.format(
                        "时间段冲突：%s %s-%s 与已有 %s-%s 重叠",
                        dayLabel(candidate.getDayOfWeek()),
                        candidate.getStartTime().format(TIME_FMT),
                        candidate.getEndTime().format(TIME_FMT),
                        b.getStartTime().format(TIME_FMT),
                        b.getEndTime().format(TIME_FMT)));
            }
        }
    }

    private static String dayLabel(Integer day) {
        String[] names = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return day != null && day >= 1 && day <= 7 ? names[day] : "未知";
    }

    private static int minutesBetween(LocalTime s, LocalTime e) {
        int m = (e.getHour() * 60 + e.getMinute()) - (s.getHour() * 60 + s.getMinute());
        return Math.max(0, m);
    }

    private static ScheduleBlock.BlockType parseType(String value) {
        if (value == null || value.isBlank()) {
            return ScheduleBlock.BlockType.FREE;
        }
        try {
            return ScheduleBlock.BlockType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ScheduleBlock.BlockType.FREE;
        }
    }

    private static ScheduleBlock.RecurrenceType parseRecurrence(String value) {
        if (value == null || value.isBlank()) {
            return ScheduleBlock.RecurrenceType.WEEKLY;
        }
        try {
            return ScheduleBlock.RecurrenceType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ScheduleBlock.RecurrenceType.WEEKLY;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException("日期格式错误，应为 yyyy-MM-dd：" + value);
        }
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException("时间格式错误，应为 HH:MM（00:00-23:59）：" + value);
        }
    }
}
