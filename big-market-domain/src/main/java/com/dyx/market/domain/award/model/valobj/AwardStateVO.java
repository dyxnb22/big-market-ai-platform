package com.dyx.market.domain.award.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户中奖记录（{@code user_award_record}）的发奖状态。
 * <p>状态语义边界（详见 {@code docs/data-and-outbox.md}）：</p>
 * <ul>
 *   <li>{@code create} — 抽奖落库时写入，表示待发奖</li>
 *   <li>{@code complete} — 本地发奖逻辑执行完毕（如积分任务入队），<b>不等于</b>积分已到账 account</li>
 *   <li>{@code fail} — 发奖失败，需人工或对账处理</li>
 * </ul>
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @create 2024-04-06 09:13
 */
@Getter
@AllArgsConstructor
public enum AwardStateVO {

    /** 抽奖落库，待发奖 */
    create("create", "创建"),
    /** 本地发奖完成（积分类奖品此时仅写入 credit_award_task，尚未 RPC 入账） */
    complete("complete", "发奖完成"),
    /** 发奖失败 */
    fail("fail", "发奖失败"),
    ;

    private final String code;
    private final String desc;

    public static AwardStateVO getByCode(String code) {
        if (code == null) return null;
        for (AwardStateVO value : values()) {
            if (value.code.equals(code)) return value;
        }
        return null;
    }

}
