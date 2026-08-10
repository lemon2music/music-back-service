# MEMORY.md

This file indexes important design documents and implementation plans for the Lemon Music backend service.

## IAP (In-App Purchase) System

### HarmonyOS IAP Integration
- **Design**: `docs/superpowers/specs/2026-07-23-harmonyos-iap-integration-design.md`
  - Initial IAP system design for HarmonyOS integration
  - Covers pre-order, purchase verification, and fulfillment flows

### IAP Order Cancellation Feature  
- **Design**: `docs/superpowers/specs/2026-08-10-iap-order-cancellation-design.md`
  - Complete design for IAP order cancellation functionality
  - Details client-server interaction for handling user payment cancellation
  - Includes security design, testing strategy, and deployment considerations
- **Implementation Plan**: `docs/superpowers/plans/2026-08-10-iap-order-cancellation-implementation.md`
  - Step-by-step implementation plan spanning 11 tasks
  - Covers service-side (Tasks 1-7) and client-side (Tasks 8-9) implementation
  - Includes integration testing (Task 10) and documentation (Task 11)

## Membership System

### Membership System Design
- **Implementation Plan**: `docs/superpowers/plans/2025-01-23-membership-system.md`
  - Complete membership system with VIP levels, points, and subscriptions
  - Scheduled tasks for daily points processing
  - Product system with effect-based configuration
