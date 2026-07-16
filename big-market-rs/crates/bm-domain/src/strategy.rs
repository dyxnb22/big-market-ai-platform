//! Weighted raffle strategy helpers.

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
        }];
    }
    vec![
        AwardWeight {
            award_id: 101,
            award_title: "积分5".into(),
            award_index: 1,
            weight: 10,
            credit_amount: money("5.00"),
        },
        AwardWeight {
            award_id: 102,
            award_title: "积分10".into(),
            award_index: 2,
            weight: 5,
            credit_amount: money("10.00"),
        },
        AwardWeight {
            award_id: 103,
            award_title: "谢谢参与".into(),
            award_index: 3,
            weight: 85,
            credit_amount: money("0.00"),
        },
    ]
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stage_is_deterministic_single_weight() {
        let w = default_stage_weights(100401);
        assert_eq!(w.len(), 1);
        assert_eq!(pick_weighted(&w).unwrap().award_id, 101);
    }
}
