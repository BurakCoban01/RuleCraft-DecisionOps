// Fictional policy thresholds for product exploration, not lending guidance.
export const projectKey = 'RETAIL-LENDING';
export const serviceKey = 'LOAN_OFFER';
export const policyVersions = [
  { label: 'Initial lending policy', affordability: '0.45', scoreFloor: 600, note: 'Establish eligibility, exposure controls and ordered risk decisions.' },
  { label: 'Affordability refinement', affordability: '0.40', scoreFloor: 600, note: 'Reduce total monthly commitment limit from 45% to 40%.' },
  { label: 'Reviewed credit-risk policy', affordability: '0.40', scoreFloor: 620, note: 'Raise the credit floor from 600 to 620; retain explicit referral boundaries.' },
];

export const expressions = version => ({
  eligibility: 'application.applicant.age >= 21 and application.applicant.residency = "RESIDENT" and application.applicant.employmentTenure >= 6',
  affordability: `application.finances.monthlyIncome > 0 and (application.finances.monthlyObligations + application.request.amount / application.request.termMonths) / application.finances.monthlyIncome <= ${version.affordability}`,
  riskBand: `if application.credit.fraudFlag then "FRAUD" else if application.credit.delinquencies >= 2 then "ADVERSE" else if application.credit.score < ${version.scoreFloor} then "WEAK" else if application.credit.score < 680 or application.credit.recentInquiries > 5 then "BORDERLINE" else if application.credit.score >= 760 then "PRIME" else "STANDARD"`,
  exposure: 'application.request.amount + application.finances.existingExposure <= 60000',
  offerRate: 'if decision = "APPROVE" then (if riskBand = "PRIME" then 6.9 else 9.9) else 0',
});

export const policyRules = [
  { when: { eligible: false }, result: 'DECLINE', description: 'Decline outside age, residency or tenure policy' },
  { when: { riskBand: 'FRAUD' }, result: 'DECLINE', description: 'Decline applications flagged for fraud risk' },
  { when: { riskBand: 'ADVERSE' }, result: 'DECLINE', description: 'Decline severe recent delinquency' },
  { when: { riskBand: 'WEAK' }, result: 'DECLINE', description: 'Decline credit profiles below the policy floor' },
  { when: { affordable: false }, result: 'DECLINE', description: 'Decline unaffordable monthly commitments' },
  { when: { withinExposure: false }, result: 'REFER', description: 'Refer exposure above the delegated limit' },
  { when: { riskBand: 'BORDERLINE' }, result: 'REFER', description: 'Refer borderline score or inquiry profiles' },
  { when: { riskBand: 'PRIME' }, result: 'APPROVE', description: 'Approve prime-risk applicants within limits' },
  { when: { riskBand: 'STANDARD' }, result: 'APPROVE', description: 'Approve standard-risk applicants within limits' },
  { when: {}, result: 'REFER', description: 'Refer any remaining policy exception' },
];

export function application(patch = {}) {
  const base = {
    applicant: { age: 34, residency: 'RESIDENT', employmentType: 'SALARIED', employmentTenure: 48 },
    credit: { score: 785, delinquencies: 0, recentInquiries: 1, fraudFlag: false },
    finances: { monthlyIncome: 6000, monthlyObligations: 1000, existingExposure: 5000 },
    request: { amount: 18000, termMonths: 36, purpose: 'HOME_IMPROVEMENT', channel: 'BRANCH' },
  };
  return Object.fromEntries(Object.entries(base).map(([key, value]) => [key, { ...value, ...patch[key] }]));
}

export const scenarios = [
  ['primeSalaried', 'Prime salaried applicant', {}, 'APPROVE', 6.9],
  ['selfEmployed', 'Established self-employed applicant', { applicant: { employmentType: 'SELF_EMPLOYED', employmentTenure: 60 }, credit: { score: 725 } }, 'APPROVE', 9.9],
  ['highCommitments', 'High monthly commitments', { finances: { monthlyObligations: 2300 } }, 'DECLINE', 0],
  ['severeDelinquency', 'Severe recent delinquency', { credit: { delinquencies: 2 } }, 'DECLINE', 0],
  ['thinCreditFile', 'Thin credit file', { credit: { score: 610 } }, 'DECLINE', 0],
  ['borderlineScore', 'Borderline credit score', { credit: { score: 660 } }, 'REFER', 0],
  ['nonResident', 'Non-resident applicant', { applicant: { residency: 'NON_RESIDENT' } }, 'DECLINE', 0],
  ['underPolicyAge', 'Below minimum policy age', { applicant: { age: 20 } }, 'DECLINE', 0],
  ['highExposure', 'Requested exposure above limit', { request: { amount: 58000, termMonths: 60 } }, 'REFER', 0],
  ['exposureBoundary', 'Exact delegated exposure limit', { finances: { existingExposure: 42000 } }, 'APPROVE', 6.9],
  ['manyInquiries', 'Recent inquiry referral boundary', { credit: { recentInquiries: 6 } }, 'REFER', 0],
  ['fraudFlag', 'Fraud-risk rejection', { credit: { fraudFlag: true } }, 'DECLINE', 0],
  ['scoreFloor', 'Exact credit-floor boundary', { credit: { score: 620 } }, 'REFER', 0],
  ['standardBoundary', 'Exact standard-risk boundary', { credit: { score: 680 } }, 'APPROVE', 9.9],
  ['commitmentBoundary', 'Exact affordability boundary', { finances: { monthlyIncome: 5000, monthlyObligations: 1500 } }, 'APPROVE', 6.9],
  ['commitmentRefinement', 'Refined affordability limit', { finances: { monthlyIncome: 5000, monthlyObligations: 1650 } }, 'DECLINE', 0],
  ['shortTenure', 'Employment tenure below policy', { applicant: { employmentTenure: 5 } }, 'DECLINE', 0],
  ['zeroIncome', 'Zero-income protection', { finances: { monthlyIncome: 0 } }, 'DECLINE', 0],
].map(([testKey, name, patch, decision, annualRate]) => ({ testKey, name, inputs: { application: application(patch) }, expectedOutputs: { decision, annualRate } }));
