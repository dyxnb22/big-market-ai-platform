//! Weighted raffle strategy helpers + lock-rule filtering.

use crate::ports::AwardWeight;
use bm_types::money;
use rand::Rng;

pub fn default_stage_weights(activity_id: i64) -> Vec<AwardWeight> {
    if activity_id == 100401 {
        // Deterministic path for learning smoke: single award weight 1.
        return vec![AwardWeight {
            award_id: 101,
            award_title: "1等奖：积分5".into(),
            award_index: 1,
            weight: 1,
            credit_amount: money("5.00"),
            rule_model: Some("tree_luck_award".into()),
        }];
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
}
