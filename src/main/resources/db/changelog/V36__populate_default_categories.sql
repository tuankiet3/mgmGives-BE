-- Populate default and comprehensive categories for fundraising campaigns
INSERT INTO categories (name, description, status) VALUES
-- Existing categories (included for completeness, skipped if already present via ON CONFLICT)
('Disaster Relief', 'Emergency response and recovery support for natural disasters.', 'APPROVED'),
('Education', 'Providing books, scholarships, and resources to children in need.', 'APPROVED'),
('Healthcare', 'Medical aid, supplies, and services for underprivileged communities.', 'APPROVED'),

-- Arts, Culture & Community
('Community Development', 'Support local infrastructure, public facilities, and community-driven initiatives.', 'APPROVED'),
('Arts & Culture Preservation', 'Supporting local artists, heritage sites, historical preservation, and cultural programs.', 'APPROVED'),
('Sports & Recreation', 'Community sports programs, youth athletics, and recreational facilities.', 'APPROVED'),

-- Innovation & Nature
('Environmental Conservation', 'Projects dedicated to preserving ecosystems, forestry, clean energy, and eco-friendly initiatives.', 'APPROVED'),
('Animal Rescue & Shelter', 'Dedicated to animal rescue operations, shelters, and veterinary care.', 'APPROVED'),
('Technology for Good', 'Projects using technology to solve social, environmental, or community problems.', 'APPROVED'),

-- Social Causes & Humanitarian
('Poverty Alleviation', 'Providing basic needs such as food, clean water, clothing, and shelter to disadvantaged individuals.', 'APPROVED'),
('Children & Youth', 'Programs focused on child welfare, youth development, and orphanage support.', 'APPROVED'),
('Refugee & Displacement Support', 'Aid, housing, and integration support for refugees and displaced families.', 'APPROVED'),
('Women''s Empowerment', 'Programs supporting women''s rights, safety, education, and economic independence.', 'APPROVED'),
('Elderly Care', 'Support for aging individuals, including care homes, medical aid, and companionship programs.', 'APPROVED'),
('Human Rights & Advocacy', 'Legal aid, awareness campaigns, and advocacy for marginalized or vulnerable groups.', 'APPROVED'),

-- Health & Medical (Granular)
('Medical Treatment & Surgery', 'Support for individuals needing costly treatments, surgeries, or ongoing medical care.', 'APPROVED'),
('Mental Health & Wellness', 'Counseling access, mental health awareness, and wellness support programs.', 'APPROVED'),
('Disability Support', 'Assistive devices, accessibility improvements, and specialized care for people with disabilities.', 'APPROVED'),

-- Education & Skills (Additional)
('Vocational Training & Livelihood', 'Job skills training, vocational education, and small business/livelihood support.', 'APPROVED'),

-- Disaster & Crisis
('Disaster Relief & Recovery', 'Rebuilding, emergency supplies, and recovery support after natural or man-made disasters.', 'APPROVED'),
('Crisis & Emergency', 'Immediate support and assistance for personal emergencies, accidents, or urgent non-medical crises.', 'APPROVED'),

-- Faith & Memorial
('Religious & Faith-Based Causes', 'Support for places of worship, religious programs, and faith-based charitable initiatives.', 'APPROVED'),
('Memorial & Tribute Funds', 'Funds raised in memory of a loved one for a related charitable cause.', 'APPROVED'),

-- Catch-all
('Other / General Cause', 'Charitable fundraising campaigns that do not fit into any specific category.', 'APPROVED')
ON CONFLICT (LOWER(name)) WHERE deleted_at IS NULL DO NOTHING;
