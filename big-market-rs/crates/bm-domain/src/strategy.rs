//! Weighted raffle strategy helpers + lock-rule filtering + chain lite.

use crate::ports::AwardWeight;
use bm_types::money;
use rand::Rng;
use std::collections::HashMap;

pub fn default_stage_weights(activity_id: i64) -> Vec<AwardWeight> {
    if activity_id == 100401 {
        // Deterministic path for smoke: single award weight 1.
        return vec![AwardWeight {
            award_id: 101,
            award_title: "1等奖：积分5".into(),
            award_index: 1,
            weight: 1,
            credit_amount: money("5.00"),
            rule_model: Some("tree_luck_award".into()),
        }];
    }
    if activity_id == 100402 {
        // Interview demo: multi-weight + lock rules (channel c02/s02).
        return vec![
            AwardWeight {
                award_id: 201,
                award_title: "积分5".into(),
                award_index: 1,
                weight: 40,
                credit_amount: money("5.00"),
                rule_model: Some("tree_luck_award".into()),
            },
            AwardWeight {
                award_id: 202,
                award_title: "积分10".into(),
                award_index: 2,
                weight: 25,
                credit_amount: money("10.00"),
                rule_model: Some("tree_lock_1".into()),
            },
            AwardWeight {
                award_id: 203,
                award_title: "积分20".into(),
                award_index: 3,
                weight: 15,
                credit_amount: money("20.00"),
                rule_model: Some("tree_lock_3".into()),
            },
            AwardWeight {
                award_id: 204,
                award_title: "谢谢参与".into(),
                award_index: 4,
                weight: 20,
                credit_amount: money("0.00"),
                rule_model: Some("tree_luck_award".into()),
            },
        ];
    }
    vec![
        AwardWeight {
            award_id: 101,
            award_title: "积分5".into(),
            award_index: 1,
            weight: 10,
            credit_amount: money("5.00"),
            rule_model: Some("tree_luck_award".into()),
        },
        AwardWeight {
            award_id: 102,
            award_title: "积分10".into(),
            award_index: 2,
            weight: 5,
            credit_amount: money("10.00"),
            rule_model: Some("tree_luck_award".into()),
        },
        AwardWeight {
            award_id: 103,
            award_title: "谢谢参与".into(),
            award_index: 3,
            weight: 85,
            credit_amount: money("0.00"),
            rule_model: Some("tree_luck_award".into()),
        },
    ]
}

/// Parse `tree_lock_N` → minimum prior draws required (inclusive).
pub fn lock_threshold(rule_model: &str) -> Option<i32> {
    let rest = rule_model.strip_prefix("tree_lock_")?;
    rest.parse().ok()
}

/// Lock visibility for award list (award-list lock fields).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AwardLockView {
    pub award_rule_lock_count: Option<i32>,
    pub is_award_unlock: bool,
    pub wait_unlock_count: i32,
    pub subtitle: String,
}

pub fn award_lock_view(rule_model: Option<&str>, prior_draws: i32) -> AwardLockView {
    let Some(model) = rule_model else {
        return AwardLockView {
            award_rule_lock_count: None,
            is_award_unlock: true,
            wait_unlock_count: 0,
            subtitle: String::new(),
        };
    };
    match lock_threshold(model) {
        Some(need) => {
            let unlocked = prior_draws >= need;
            let wait = if unlocked { 0 } else { need - prior_draws };
            AwardLockView {
                award_rule_lock_count: Some(need),
                is_award_unlock: unlocked,
                wait_unlock_count: wait,
                subtitle: if unlocked {
                    String::new()
                } else {
                    format!("抽奖{need}次后解锁")
                },
            }
        }
        None => AwardLockView {
            award_rule_lock_count: None,
            is_award_unlock: true,
            wait_unlock_count: 0,
            subtitle: String::new(),
        },
    }
}

/// Filter awards by lock rules using prior draw count (before current draw).
pub fn filter_lock_rules(weights: &[AwardWeight], prior_draws: i32) -> Vec<AwardWeight> {
    weights
        .iter()
        .filter(|w| {
            let Some(model) = w.rule_model.as_deref() else {
                return true;
            };
            match lock_threshold(model) {
                Some(need) => prior_draws >= need,
                None => true, // tree_luck_award and unknown models stay eligible
            }
        })
        .cloned()
        .collect()
}

pub fn pick_weighted(weights: &[AwardWeight]) -> Option<&AwardWeight> {
    let total: u32 = weights.iter().map(|w| w.weight).sum();
    if total == 0 || weights.is_empty() {
        return None;
    }
    let mut roll = rand::thread_rng().gen_range(0..total);
    for w in weights {
        if roll < w.weight {
            return Some(w);
        }
        roll -= w.weight;
    }
    weights.last()
}

/// Apply lock filter then weighted pick.
pub fn pick_for_user(weights: &[AwardWeight], prior_draws: i32) -> Option<AwardWeight> {
    let eligible = filter_lock_rules(weights, prior_draws);
    pick_weighted(&eligible).cloned()
}

/// `BM_STRATEGY_CHAIN=1` enables blacklist / weight-bucket lite (never on demo 100401).
pub fn strategy_chain_enabled() -> bool {
    match std::env::var("BM_STRATEGY_CHAIN") {
        Ok(v) => v == "1" || v.eq_ignore_ascii_case("true"),
        Err(_) => false,
    }
}

/// Parse `rule_blacklist` value: `101:user001,user002`.
pub fn parse_blacklist_rule(value: &str) -> HashMap<String, i32> {
    let mut out = HashMap::new();
    let value = value.trim();
    if value.is_empty() {
        return out;
    }
    let Some((award_s, users)) = value.split_once(':') else {
        return out;
    };
    let Ok(award_id) = award_s.trim().parse::<i32>() else {
        return out;
    };
    for u in users.split(',') {
        let u = u.trim();
        if !u.is_empty() {
            out.insert(u.to_string(), award_id);
        }
    }
    out
}

/// Parse `rule_weight` value: `60:102,103 200:106,107`.
/// Thresholds are minimum prior draws; highest matching bucket wins.
pub fn parse_weight_buckets(value: &str) -> Vec<(i32, Vec<i32>)> {
    let mut buckets = Vec::new();
    for part in value.split_whitespace() {
        let Some((th_s, ids_s)) = part.split_once(':') else {
            continue;
        };
        let Ok(th) = th_s.parse::<i32>() else {
            continue;
        };
        let ids: Vec<i32> = ids_s
            .split(',')
            .filter_map(|s| s.trim().parse().ok())
            .collect();
        if !ids.is_empty() {
            buckets.push((th, ids));
        }
    }
    buckets.sort_by_key(|(th, _)| *th);
    buckets
}

/// Restrict weights to the highest bucket whose threshold <= prior_draws.
/// If no bucket matches, return original weights unchanged.
pub fn apply_weight_bucket(
    weights: &[AwardWeight],
    prior_draws: i32,
    rule_value: &str,
) -> Vec<AwardWeight> {
    let buckets = parse_weight_buckets(rule_value);
    let mut chosen: Option<&[i32]> = None;
    for (th, ids) in &buckets {
        if prior_draws >= *th {
            chosen = Some(ids.as_slice());
        }
    }
    let Some(ids) = chosen else {
        return weights.to_vec();
    };
    let filtered: Vec<AwardWeight> = weights
        .iter()
        .filter(|w| ids.contains(&w.award_id))
        .cloned()
        .collect();
    if filtered.is_empty() {
        weights.to_vec()
    } else {
        filtered
    }
}

/// Build rule-weight list views (rule-weight response shape).
/// Returns `(threshold_count, [(award_id, title), ...])` per bucket.
pub fn rule_weight_list_views(
    rule_value: &str,
    awards: &[AwardWeight],
) -> Vec<(i32, Vec<(i32, String)>)> {
    let title_by_id: HashMap<i32, String> = awards
        .iter()
        .map(|w| (w.award_id, w.award_title.clone()))
        .collect();
    parse_weight_buckets(rule_value)
        .into_iter()
        .map(|(th, ids)| {
            let list: Vec<(i32, String)> = ids
                .into_iter()
                .map(|id| {
                    let title = title_by_id
                        .get(&id)
                        .cloned()
                        .unwrap_or_else(|| format!("award:{id}"));
                    (id, title)
                })
                .collect();
            (th, list)
        })
        .collect()
}

/// Observability for interview / debug: which lite rules touched the pool.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct StrategyTrace {
    pub prior_draws: i32,
    pub pool_before: usize,
    pub pool_after: usize,
    pub rules_applied: Vec<String>,
    pub picked_rule_model: Option<String>,
}

/// Optional chain lite before lock+weight pick. Demo activity 100401 always skips chain env.
pub fn pick_with_chain_lite(
    weights: &[AwardWeight],
    prior_draws: i32,
    user_id: &str,
    activity_id: i64,
) -> Option<AwardWeight> {
    pick_with_chain_lite_traced(weights, prior_draws, user_id, activity_id).map(|(w, _)| w)
}

/// Same as [`pick_with_chain_lite`] plus a rule-application trace.
pub fn pick_with_chain_lite_traced(
    weights: &[AwardWeight],
    prior_draws: i32,
    user_id: &str,
    activity_id: i64,
) -> Option<(AwardWeight, StrategyTrace)> {
    let mut trace = StrategyTrace {
        prior_draws,
        pool_before: weights.len(),
        pool_after: weights.len(),
        rules_applied: Vec::new(),
        picked_rule_model: None,
    };

    if activity_id == 100401 || !strategy_chain_enabled() {
        let eligible = filter_lock_rules(weights, prior_draws);
        if eligible.len() != weights.len() {
            trace.rules_applied.push("tree_lock".into());
        }
        trace.pool_after = eligible.len();
        let picked = pick_weighted(&eligible)?.clone();
        trace.picked_rule_model = picked.rule_model.clone();
        return Some((picked, trace));
    }

    let mut pool = weights.to_vec();
    if let Ok(bl) = std::env::var("BM_RULE_BLACKLIST") {
        let map = parse_blacklist_rule(&bl);
        if let Some(&award_id) = map.get(user_id) {
            trace.rules_applied.push("rule_blacklist".into());
            if let Some(w) = pool.iter().find(|w| w.award_id == award_id) {
                let picked = w.clone();
                trace.pool_after = 1;
                trace.picked_rule_model = picked.rule_model.clone();
                return Some((picked, trace));
            }
            let picked = AwardWeight {
                award_id,
                award_title: format!("blacklist:{award_id}"),
                award_index: 0,
                weight: 1,
                credit_amount: money("0.00"),
                rule_model: Some("rule_blacklist".into()),
            };
            trace.pool_after = 1;
            trace.picked_rule_model = Some("rule_blacklist".into());
            return Some((picked, trace));
        }
    }
    if let Ok(wv) = std::env::var("BM_RULE_WEIGHT") {
        let before = pool.len();
        pool = apply_weight_bucket(&pool, prior_draws, &wv);
        if pool.len() != before {
            trace.rules_applied.push("rule_weight".into());
        }
    }
    let eligible = filter_lock_rules(&pool, prior_draws);
    if eligible.len() != pool.len() {
        trace.rules_applied.push("tree_lock".into());
    }
    trace.pool_after = eligible.len();
    let picked = pick_weighted(&eligible)?.clone();
    trace.picked_rule_model = picked.rule_model.clone();
    Some((picked, trace))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stage_is_deterministic_single_weight() {
        let w = default_stage_weights(100401);
        assert_eq!(w.len(), 1);
        assert_eq!(pick_weighted(&w).unwrap().award_id, 101);
    }

    #[test]
    fn lock_demo_activity_has_locks() {
        let w = default_stage_weights(100402);
        assert_eq!(w.len(), 4);
        assert!(w.iter().any(|a| a.rule_model.as_deref() == Some("tree_lock_3")));
        let unlocked = filter_lock_rules(&w, 0);
        assert_eq!(unlocked.len(), 2); // 201 + 204
        let after_one = filter_lock_rules(&w, 1);
        assert_eq!(after_one.len(), 3); // +202
        let after_three = filter_lock_rules(&w, 3);
        assert_eq!(after_three.len(), 4);
    }

    #[test]
    fn lock_excludes_until_threshold() {
        let weights = vec![
            AwardWeight {
                award_id: 1,
                award_title: "luck".into(),
                award_index: 1,
                weight: 1,
                credit_amount: money("1.00"),
                rule_model: Some("tree_luck_award".into()),
            },
            AwardWeight {
                award_id: 2,
                award_title: "locked".into(),
                award_index: 2,
                weight: 1,
                credit_amount: money("0.00"),
                rule_model: Some("tree_lock_2".into()),
            },
        ];
        let at_one = filter_lock_rules(&weights, 1);
        assert_eq!(at_one.len(), 1);
        assert_eq!(at_one[0].award_id, 1);
        let at_two = filter_lock_rules(&weights, 2);
        assert_eq!(at_two.len(), 2);
    }

    #[test]
    fn award_lock_view_wait_counts() {
        let locked = award_lock_view(Some("tree_lock_3"), 1);
        assert_eq!(locked.award_rule_lock_count, Some(3));
        assert!(!locked.is_award_unlock);
        assert_eq!(locked.wait_unlock_count, 2);
        let open = award_lock_view(Some("tree_lock_3"), 3);
        assert!(open.is_award_unlock);
        assert_eq!(open.wait_unlock_count, 0);
        let luck = award_lock_view(Some("tree_luck_award"), 0);
        assert!(luck.is_award_unlock);
        assert_eq!(luck.award_rule_lock_count, None);
    }

    #[test]
    fn weight_bucket_picks_highest_match() {
        let weights = vec![
            AwardWeight {
                award_id: 102,
                award_title: "a".into(),
                award_index: 1,
                weight: 1,
                credit_amount: money("0"),
                rule_model: None,
            },
            AwardWeight {
                award_id: 106,
                award_title: "b".into(),
                award_index: 2,
                weight: 1,
                credit_amount: money("0"),
                rule_model: None,
            },
            AwardWeight {
                award_id: 101,
                award_title: "c".into(),
                award_index: 3,
                weight: 1,
                credit_amount: money("0"),
                rule_model: None,
            },
        ];
        let at_70 = apply_weight_bucket(&weights, 70, "10:102,103 70:106,107 1000:104");
        assert_eq!(at_70.len(), 1);
        assert_eq!(at_70[0].award_id, 106);
        let at_5 = apply_weight_bucket(&weights, 5, "10:102,103 70:106,107");
        assert_eq!(at_5.len(), 3); // no bucket → unchanged
    }

    #[test]
    fn blacklist_maps_users() {
        let m = parse_blacklist_rule("101:user001,user002");
        assert_eq!(m.get("user001"), Some(&101));
        assert_eq!(m.get("user002"), Some(&101));
    }

    #[test]
    fn pick_for_user_respects_lock() {
        let w = default_stage_weights(100402);
        let picked = pick_for_user(&w, 0).unwrap();
        assert!(picked.award_id == 201 || picked.award_id == 204);
    }

    #[test]
    fn chain_blacklist_forces_award() {
        static LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());
        let _guard = LOCK.lock().unwrap_or_else(|e| e.into_inner());
        std::env::set_var("BM_STRATEGY_CHAIN", "1");
        std::env::set_var("BM_RULE_BLACKLIST", "203:xiaofuge");
        let w = default_stage_weights(100402);
        let (picked, trace) =
            pick_with_chain_lite_traced(&w, 0, "xiaofuge", 100402).unwrap();
        assert_eq!(picked.award_id, 203);
        assert!(trace.rules_applied.iter().any(|r| r == "rule_blacklist"));
        std::env::remove_var("BM_RULE_BLACKLIST");
        std::env::remove_var("BM_STRATEGY_CHAIN");
    }

    #[test]
    fn rule_weight_list_views_buckets() {
        let w = default_stage_weights(100402);
        let views = rule_weight_list_views("1:202 3:203", &w);
        assert_eq!(views.len(), 2);
        assert_eq!(views[0].0, 1);
        assert_eq!(views[0].1[0].0, 202);
    }
}
