# Architecture Decision Record (ADR) Process

This document describes how the Architecture Decision Agent manages ADRs and maintains architectural documentation for the microservices ecosystem.

## Overview

The Architecture Decision Agent is responsible for:
1. **Identifying** architecturally significant changes
2. **Documenting** decisions in ADRs
3. **Maintaining** the Architecture Decision Log
4. **Coordinating** cross-service impacts
5. **Communicating** decisions to the team

## What Qualifies for an ADR?

### High Priority (Create ADR)
- API contract changes (request/response format, new endpoints, breaking changes)
- Changes to inter-service communication (order-service ↔ payment-service)
- Database schema changes affecting queries or ORM
- Major dependency version upgrades with architectural implications
- Service topology or infrastructure changes
- Authentication/authorization policy changes

### Medium Priority (Likely Create ADR)
- Significant refactoring of service layers (controller/service/repository changes)
- Introduction of new patterns or frameworks
- Performance optimizations with significant trade-offs
- Error handling or resilience mechanism changes

### Lower Priority (Optional)
- Bug fixes with no architectural impact
- Documentation updates only
- Test-only changes
- Code style/formatting changes

## Workflow

### Step 1: Detect Architectural Change
The agent monitors:
- New pull requests and merge requests
- Commit messages for keywords (breaking, refactor, major, api, contract)
- File changes in strategic locations (controllers, services, models, config)
- Branch names indicating feature/refactor work

### Step 2: Analyze Impact
For each detected change, determine:
- Does it involve an API contract change?
- Does it affect inter-service communication?
- Does it introduce new patterns?
- Which services are affected?
- What are the positive/negative consequences?
- Are there viable alternatives?

### Step 3: Create ADR
If the change is architecturally significant:
1. Determine next ADR ID from `adr/INDEX.md`
2. Create file: `adr/NNNN-short-title.md`
3. Copy template and fill all sections
4. Document cross-service impacts explicitly
5. Link to relevant PRs, commits, and issues

### Step 4: Update ADR Index
1. Add entry to `adr/INDEX.md` table
2. Include: ID, title, status, affected services, date, filename
3. Commit the ADR file with the index

### Step 5: Update Architecture Decision Log
1. Add entry to `ARCHITECTURE_DECISION_LOG.md`
2. Format: date | service(s) | change type | summary | link to ADR
3. Commit the update

### Step 6: Communicate
1. Comment on relevant PRs with ADR reference
2. Notify affected teams of cross-service impacts
3. Flag any coordination needs or timing concerns

## ADR Lifecycle

```
Proposed → Accepted → Active
                    ↓
                 Deprecated (superseded by newer ADR)
```

### Status Transitions

**Proposed → Accepted**
- When the decision is agreed upon by the team
- When the implementing PR is merged
- Status is updated in ADR file and INDEX.md

**Active → Deprecated**
- When a newer ADR supersedes this one
- The old ADR is marked as "Superseded by ADR NNNN"
- The new ADR links back to the superseded ADR

## Cross-Service Coordination

When a change affects both order-service and payment-service:

1. **Document in both ADRs** if separate ADRs are created
2. **Highlight the contract** (API format, timing, sequencing)
3. **Flag timing concerns**:
   - Can payment-service changes deploy independently?
   - Does order-service need to deploy first/last?
   - Are there backward-compatibility windows?
4. **Track dependencies** in "Related Decisions" section

### Example: API Contract Change

If order-service changes the payment request format:
- ✅ Create ADR documenting why the change was needed
- ✅ Document that payment-service must be updated to handle new format
- ✅ Specify timing: payment-service must be updated before order-service cutover
- ✅ Link the two ADRs together in "Related Decisions"
- ✅ Note in INDEX.md that this affects both services

## Template Structure Reference

Each ADR must include:

| Section | Purpose |
|---------|---------|
| Header | Metadata (date, status, services, ID) |
| Context | The problem and why it matters |
| Decision | What we decided and why |
| Consequences | Positive, negative, neutral impacts |
| Cross-Service Impact | Effects on other services (if any) |
| Alternatives Considered | Other options and why they were rejected |
| Related Decisions | Links to related ADRs, MRs, issues |
| Notes | Additional context or follow-ups |

## Agent Configuration

The agent is configured with:
- **Repository Access**: Read/write to all three repositories (order-service, payment-service, architecture-decisions)
- **Change Detection**: Monitors commits, PRs, branch names for architectural significance
- **Numbering**: Uses INDEX.md for ID assignment
- **Format**: Enforces TEMPLATE.md structure
- **Update Frequency**: Checks for new changes continuously

## Metrics & Reporting

The agent tracks:
- ADR creation latency (time from PR to ADR documentation)
- Coverage (% of architectural changes documented)
- Cross-service impact identification accuracy
- ADR status distribution (proposed vs. accepted vs. deprecated)

## Tools & Integration

- **Git/GitLab**: Repository monitoring and MR analysis
- **Markdown**: ADR documents and log files
- **Index files**: Track metadata and relationships
- **Commit messages**: Parse for architectural keywords
