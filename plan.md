# Architecture Decision Agent — Plan

## Main Goal

The Architecture Decision Agent is responsible for **tracking, documenting, and coordinating architectural decisions** across the microservices ecosystem (order-service, payment-service). The agent helps ensure that:
1. Significant architectural changes are properly documented in ADRs (Architecture Decision Records)
2. The Architecture Decision Log (ADL) is kept up-to-date with all decisions
3. Cross-service architectural impacts are identified and communicated
4. Architectural consistency and patterns are maintained across services

## Core Responsibilities

### 1. Monitor and Identify Architectural Changes
- Track pull requests and changes across both microservices
- Identify commits/PRs that represent architecturally significant decisions
- Recognize patterns: API contract changes, new service integrations, data model changes, communication pattern changes, dependency upgrades, infrastructure changes

### 2. Document Decisions in ADRs
- Create Architecture Decision Records (ADRs) for significant changes
- Store ADRs in `architecture-decisions/adr/` directory (following standard naming: `AAAA-title-of-decision.md`)
- Each ADR should include:
  - **Status**: Proposed, Accepted, Deprecated, Superseded
  - **Context**: What problem we're solving, why this decision matters
  - **Decision**: What we decided and why this specific approach
  - **Consequences**: Positive and negative impacts, especially on other services
  - **Alternatives Considered**: Other options we evaluated
  - **Cross-Service Impact**: How this affects other services (order-service ↔ payment-service interactions)

### 3. Maintain the Architecture Decision Log (ADL)
- Update `architecture-decisions/ARCHITECTURE_DECISION_LOG.md` for each decision
- Log entry includes: date, affected service(s), change type, summary, link to ADR
- Provide a quick-reference timeline of all architectural decisions

### 4. Coordinate Cross-Service Changes
- When a change in one service affects the other (e.g., API contract change), document the coordination
- Example: If order-service changes how it calls payment-service, both services are affected
- Ensure both services' teams understand the contract/interface changes
- Track dependencies between services

### 5. Detect and Flag Architectural Risks
- Identify potential issues: tight coupling increases, breaking changes, deprecated patterns
- Flag decisions that conflict with established architectural patterns
- Alert teams to timing/sequencing issues when changes must coordinate across services

## Implementation Steps

### Phase 1: Setup and Configuration (Foundation)
1. **Configure Agent Access**
   - Read access to both service repositories and ADL
   - Write access to architecture-decisions repository
   - Ability to parse and analyze git history, PR content, and commits

2. **Initialize ADR Templates**
   - Define standard ADR format/template
   - Configure ADR ID numbering scheme (e.g., 0001, 0002...)
   - Set up directory structure in `architecture-decisions/adr/`

### Phase 2: Historical Analysis (Baseline)
3. **Analyze Existing Codebase**
   - Review current architecture of order-service and payment-service
   - Identify the inter-service contract (how they communicate)
   - Document current patterns and constraints
   - Create baseline ADRs for existing major decisions (why services are separate, current REST API design, etc.)

4. **Populate Initial ADL**
   - Add entries for any known architectural changes (past MRs, branches, decisions)
   - Identify from git history what major changes led to current state

### Phase 3: Ongoing Monitoring (Active)
5. **Track New PRs and Changes**
   - Monitor new branches and merge requests
   - Analyze each PR for architectural significance:
     - Does it change an API contract?
     - Does it introduce new inter-service communication?
     - Does it refactor significant components?
     - Does it modify data models?
     - Does it introduce new dependencies?

6. **Create ADRs for Significant Changes**
   - For each architectural change:
     - Create detailed ADR document
     - Analyze cross-service impact
     - Document alternatives considered
     - Log decision status (e.g., Accepted if PR is merged)

7. **Update Architecture Decision Log**
   - After each ADR is created, add entry to ARCHITECTURE_DECISION_LOG.md
   - Include date, service(s), change type, summary, and ADR reference

### Phase 4: Communication and Coordination
8. **Cross-Service Impact Assessment**
   - When order-service changes affect payment-service contract:
     - Document the change in both ADRs
     - Flag timing/sequencing concerns
     - Note if payment-service needs corresponding changes
   - Example scenarios:
     - Order-service changes payment request format → payment-service must adapt
     - Payment-service adds new response field → order-service should consume it
     - Either service changes timeout/retry behavior → affects reliability contract

9. **Provide Guidance to Teams**
   - Comment on PRs with architectural notes
   - Suggest improvements to architectural decisions
   - Recommend ADR creation for significant changes
   - Highlight risks or concerns from architectural perspective

### Phase 5: Maintenance and Reporting
10. **Periodic Review**
    - Review ADL for completeness and accuracy
    - Identify gaps (changes without ADRs)
    - Update status of decisions (e.g., Superseded when newer decisions replace older ones)
    - Maintain traceability between commits, PRs, and ADRs

11. **Generate Insights**
    - Track architectural trends (increasing/decreasing coupling, new patterns)
    - Identify frequently revisited decisions
    - Report on decision lifecycle (how long decisions remain current)

## Key Metrics and Success Criteria

**The agent succeeds when:**
- ✅ All architecturally significant changes are documented in ADRs within 1-2 days of PR creation
- ✅ The ARCHITECTURE_DECISION_LOG.md is always current (no unlogged changes)
- ✅ Cross-service impacts are identified and documented
- ✅ Teams can quickly understand why architectural decisions were made
- ✅ The ADL serves as reliable historical record and context for future decisions
- ✅ New team members can onboard by reading ADL and understanding the evolution of the system

## Types of Changes Requiring ADRs

**High Priority** (definitely create ADR):
- API contract changes (request/response format, new endpoints, breaking changes)
- Changes to inter-service communication protocol or mechanism
- Database schema changes that affect queries or ORM
- Dependency upgrades with architectural implications (e.g., major Spring Boot version)
- Service deployment topology or infrastructure changes
- Authentication/authorization policy changes

**Medium Priority** (likely create ADR):
- Significant refactoring of service layers (controller → service → repository changes)
- Introduction of new patterns or frameworks
- Performance optimizations with trade-offs
- Error handling or resilience mechanism changes

**Lower Priority** (optional):
- Bug fixes with no architectural impact
- Documentation updates
- Test-only changes
- Code style/formatting changes

## Technology & Tools

- **Git/GitLab**: Monitor repositories and merge requests
- **Markdown**: ADR documents and ADL in Markdown format
- **Analysis**: Parse code, commit messages, PR descriptions to detect changes
- **Communication**: Provide feedback via PR comments, ADL updates, and ADR documents
