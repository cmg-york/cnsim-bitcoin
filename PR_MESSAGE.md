# feat: Add configurable malicious behavior with confirmation delay counter

## Summary

This PR introduces a comprehensive malicious node behavior system with configurable confirmation delay counters, dynamic hashpower changes, and attack probability analysis based on the Bitcoin whitepaper. The implementation allows for sophisticated double-spending attack simulations with runtime configurability and detailed metrics collection.

## Key Features

### 1. **Configurable Confirmation Delay Counter**
- Malicious nodes now track confirmation depth before launching attacks
- Configurable via `attack.requiredConfirmations` property
- Enables testing of attack scenarios with different confirmation thresholds (0, 2, 3, 5, 6 confirmations)
- Implemented counter-based validation in `MaliciousNodeBehavior.java`

### 2. **Dynamic Hashpower Changes**
- Runtime hashpower modification support via `node.hashPowerChanges` configuration
- Format: `{nodeID:newHashPower:time, ...}`
- Enables testing of majority attacker scenarios with changing network conditions
- Integrated with event system via `Event_HashPowerChange`

### 3. **Attack Probability Analysis**
- `AttackerSuccessProbabilityAnalyzer` implements formulas from Bitcoin whitepaper Section 11
- Calculates theoretical success probability for double-spending attacks
- Considers attacker hashpower ratio and confirmation depth
- Includes comprehensive test suite validating against known scenarios

### 4. **Attack Metrics Collection**
- `AttackMetricsCollector` tracks detailed attack statistics
- Metrics include: attack timing, chain reveals, success/failure rates
- Integrated with BitcoinReporter for comprehensive logging
- Enables analysis of attack effectiveness under different conditions

### 5. **Enhanced Test Coverage**
- Added 48+ new test cases across multiple test classes
- Tests for confirmation delays (0, 2, 5 confirmations)
- Tests for high-hashpower scenarios (70% attacker power)
- Tests for dynamic hashpower changes (edge cases, simultaneous changes)
- All 82 tests passing with 100% success rate

## Technical Implementation

### Modified Files
- **BitcoinSimulatorFactory.java**: Added hashpower change scheduling and configuration parsing
- **MaliciousNodeBehavior.java**: Implemented confirmation counter logic and enhanced attack behavior
- **BitcoinNode.java**: Added getter method for blockchain access
- **Blockchain.java**: Removed unused imports for conflict registry
- **Test configurations**: Added conflict registry initialization to all test configs

### New Files
- **AttackerSuccessProbabilityAnalyzer.java**: Probability calculations per Bitcoin whitepaper
- **AttackMetricsCollector.java**: Comprehensive attack metrics tracking
- **HashPowerChangeTest.java**: Tests for dynamic hashpower modifications
- **HashPowerChangeEdgeCasesTest.java**: Edge case testing for hashpower changes
- **MaliciousBehaviorTest.java**: Comprehensive malicious behavior testing
- **EventIntegrationTest.java**: Integration tests for behavior and hashpower events
- **ConfigurationParsingTest.java**: Configuration parsing validation
- **Test configuration files**: 7+ specialized test configs for different scenarios

### Documentation
- **TEST-RESULTS.md**: Comprehensive test results for confirmation delay scenarios
- **TEST-RESULTS-70PERCENT.md**: Results for high-hashpower attacker scenarios

## Configuration Examples

### Confirmation Delay Testing
```properties
attack.requiredConfirmations = 3
attack.minChainLength = 2
attack.maxChainLength = 15
```

### Dynamic Hashpower Changes
```properties
# Node 3: 25% -> 50% at 50s -> 70% at 150s
node.hashPowerChanges = {3:5.0E10:50000, 3:7.0E10:150000}
```

### Conflict Registry (Required)
```properties
workload.hasConflicts = true
workload.conflicts.dispersion = 0.1
workload.conflicts.likelihood = 0.05
```

## Test Results

All 82 tests passing successfully:

```
Tests run: 82
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

### Test Coverage
- ✅ Blockchain structure tests (7 tests)
- ✅ Block operations tests (6 tests)
- ✅ Malicious behavior tests (6 tests)
- ✅ Hashpower change tests (4 tests)
- ✅ Attack probability analysis tests (11 tests)
- ✅ Event integration tests (8 tests)
- ✅ Configuration parsing tests (20 tests)
- ✅ Other component tests (20 tests)

## Dependencies

### cnsim-engine Requirements
This PR requires `cnsim-engine` version `0.0.2-SNAPSHOT` from the `feat/behavior-hashpower-change-events` branch, which includes:
- `TxConflictRegistry` for transaction conflict management
- `Event_HashPowerChange` for runtime hashpower modifications
- `Event_BehaviorChange` for dynamic behavior switching
- Enhanced `Simulation.getConflictRegistry()` support

## Breaking Changes

None. All changes are backward compatible with existing configurations. The new features are opt-in via configuration properties.

## Migration Guide

### For Existing Tests
If you have existing test configurations, add the following to enable conflict registry:

```properties
workload.hasConflicts = true
workload.conflicts.dispersion = 0.1
workload.conflicts.likelihood = 0.05
```

### For New Attack Simulations
1. Set `attack.requiredConfirmations` to desired confirmation depth
2. Configure malicious node: `node.createMaliciousNode = true`
3. Set hashpower ratio: `node.maliciousRatio = 0.5` (for 50%)
4. Optional: Add dynamic hashpower changes via `node.hashPowerChanges`

## Related Issues

- Implements transaction conflict detection and management
- Addresses Bitcoin whitepaper attack scenarios (Section 11)
- Enables sophisticated double-spending attack research
- Supports dynamic network condition simulation

## Checklist

- [x] Code compiles successfully
- [x] All tests pass (82/82)
- [x] Added comprehensive test coverage
- [x] Updated test configurations
- [x] Removed temporary documentation files
- [x] Merged latest changes from main branch
- [x] Fixed all test failures from merge
- [x] No breaking changes introduced
- [x] Code follows repository conventions

## Additional Notes

This implementation enables advanced research into Bitcoin-style blockchain security, particularly focusing on:
- Double-spending attack effectiveness under various confirmation policies
- Impact of attacker hashpower on network security
- Dynamic network conditions and attack adaptability
- Theoretical vs. practical attack success rates

The probability analyzer has been validated against known scenarios from the Bitcoin whitepaper, ensuring mathematical correctness of the implementation.

---

**Ready to merge** ✅

All tests passing, conflicts resolved, and cnsim-engine dependency properly configured.
