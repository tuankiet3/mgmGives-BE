-- Realistic portfolio demo data. Every person, email, transaction and activity is fictional.
-- The reserved .example domain prevents accidental delivery to real recipients.

INSERT INTO users (email, full_name, phone, role, status, failed_attempt_count, created_at, updated_at)
VALUES
 ('an.nguyen@mgmgives.example','An Nguyen','0900000101','USER','ACTIVE',0,NOW()-INTERVAL '310 days',NOW()-INTERVAL '8 days'),
 ('minh.tran@mgmgives.example','Minh Tran','0900000102','USER','ACTIVE',0,NOW()-INTERVAL '280 days',NOW()-INTERVAL '5 days'),
 ('lan.pham@mgmgives.example','Lan Pham','0900000103','USER','ACTIVE',0,NOW()-INTERVAL '260 days',NOW()-INTERVAL '4 days'),
 ('huy.le@mgmgives.example','Huy Le','0900000104','USER','ACTIVE',0,NOW()-INTERVAL '240 days',NOW()-INTERVAL '2 days'),
 ('thao.vo@mgmgives.example','Thao Vo','0900000105','USER','ACTIVE',0,NOW()-INTERVAL '220 days',NOW()-INTERVAL '9 days'),
 ('quang.do@mgmgives.example','Quang Do','0900000106','USER','ACTIVE',0,NOW()-INTERVAL '200 days',NOW()-INTERVAL '7 days'),
 ('linh.bui@mgmgives.example','Linh Bui','0900000107','USER','ACTIVE',0,NOW()-INTERVAL '175 days',NOW()-INTERVAL '3 days'),
 ('nam.hoang@mgmgives.example','Nam Hoang','0900000108','USER','ACTIVE',0,NOW()-INTERVAL '150 days',NOW()-INTERVAL '6 days'),
 ('mai.dang@mgmgives.example','Mai Dang','0900000109','USER','ACTIVE',0,NOW()-INTERVAL '125 days',NOW()-INTERVAL '1 day'),
 ('phuc.truong@mgmgives.example','Phuc Truong','0900000110','USER','ACTIVE',0,NOW()-INTERVAL '100 days',NOW()-INTERVAL '4 days'),
 ('trang.ngo@mgmgives.example','Trang Ngo','0900000111','USER','ACTIVE',0,NOW()-INTERVAL '75 days',NOW()-INTERVAL '2 days'),
 ('bao.ly@mgmgives.example','Bao Ly','0900000112','USER','ACTIVE',0,NOW()-INTERVAL '55 days',NOW()-INTERVAL '1 day')
ON CONFLICT (email) DO NOTHING;

-- Keep the dataset complete even on a brand-new database, where Java seeders run only
-- after Flyway. Existing deployments retain their original rows through the title guard.
WITH seed(title,description,status_value,start_delta,end_delta,target_value,priority_value,created_delta) AS (VALUES
 ('MGM Books & Warmth 2026','Providing school supplies, books, and warm winter jackets to children in Yen Bai province before the cold season starts.','IN_PROGRESS',-28,32,120000000,'URGENT',-35),
 ('Clean Water for Central Vietnam','Installing purification systems and restoring rainwater tanks in communities affected by drought and salinisation.','IN_PROGRESS',-52,24,250000000,'HIGH',-60),
 ('Mid-Autumn Festival Smiles 2025','A completed volunteer programme bringing books, lanterns, milk, and gift packs to children receiving hospital care.','COMPLETED',-315,-285,50000000,'NORMAL',-325)
)
INSERT INTO campaigns(title,description,user_id,status,start_date,end_date,target,priority,approved_at,approved_by,accepts_money,accepts_goods,donation_method,created_at,updated_at)
SELECT seed.title,seed.description,owner_user.id,seed.status_value::campaign_status,
 NOW()+make_interval(days=>seed.start_delta),NOW()+make_interval(days=>seed.end_delta),seed.target_value,
 seed.priority_value::campaign_priority,NOW()+make_interval(days=>seed.created_delta+4),
 (SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1),TRUE,TRUE,'MANUAL_QR',
 NOW()+make_interval(days=>seed.created_delta),NOW()-INTERVAL '1 day'
FROM seed
CROSS JOIN LATERAL (SELECT id FROM users WHERE email='an.nguyen@mgmgives.example') owner_user
WHERE NOT EXISTS(SELECT 1 FROM campaigns c WHERE c.title=seed.title);

UPDATE campaigns SET
 description='Together with partner schools in Mu Cang Chai, the team is preparing 320 learning kits, Vietnamese storybooks, and insulated jackets for primary-school pupils before the mountain winter.',
 status='IN_PROGRESS', start_date=NOW()-INTERVAL '28 days', end_date=NOW()+INTERVAL '32 days',
 approved_at=NOW()-INTERVAL '31 days', approved_by=(SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1),
 accepts_money=TRUE, accepts_goods=TRUE, donation_method='MANUAL_QR', updated_at=NOW()-INTERVAL '1 day'
WHERE title='MGM Books & Warmth 2026';

UPDATE campaigns SET
 description='Install two filtration stations and restore rainwater tanks for households in drought-affected communes of Quang Nam, with maintenance training for local technicians.',
 status='IN_PROGRESS', start_date=NOW()-INTERVAL '52 days', end_date=NOW()+INTERVAL '24 days',
 approved_at=NOW()-INTERVAL '55 days', approved_by=(SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1),
 accepts_money=TRUE, accepts_goods=FALSE, donation_method='MANUAL_QR', updated_at=NOW()-INTERVAL '2 days'
WHERE title='Clean Water for Central Vietnam';

UPDATE campaigns SET
 description='A completed volunteer campaign that delivered lanterns, books, milk, and 420 gift packs to children receiving long-term treatment at hospitals in Da Nang and Hue.',
 status='COMPLETED', start_date=NOW()-INTERVAL '315 days', end_date=NOW()-INTERVAL '285 days',
 approved_at=NOW()-INTERVAL '320 days', approved_by=(SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1),
 donation_method='MANUAL_QR', result_posted=TRUE, final_amount_raised=53000000,
 result_summary='<h3>What We Achieved</h3><p>The campaign prepared and delivered 420 Mid-Autumn gift packs across two hospital programmes.</p><h3>The Campaign Journey</h3><p>Volunteers sorted every contribution, assembled the packs, and coordinated safe delivery with hospital social-work teams.</p><h3>Closing Reflections</h3><p>Every contribution was tracked from donation to handover.</p>',
 items_summary='Donors also contributed 180 children''s books, 36 cartons of milk, and handmade lanterns.',
 acknowledgements='Thank you to every donor, volunteer, and hospital partner who made the celebration possible.',
 task_summary='Volunteers verified goods, assembled 420 packs, coordinated delivery windows, and documented the handover.',
 result_published_by=(SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1),
 result_published_at=NOW()-INTERVAL '280 days', final_donor_count=10, final_volunteer_count=8,
 updated_at=NOW()-INTERVAL '280 days'
WHERE title='Mid-Autumn Festival Smiles 2025';

WITH seed(title,description,owner_email,status_value,start_delta,end_delta,target_value,priority_value,created_delta) AS (VALUES
 ('Back-to-School Kits for Quang Nam','Equip 250 pupils in flood-prone districts with durable backpacks, notebooks, stationery, reusable water bottles, and school uniforms.','lan.pham@mgmgives.example','IN_PROGRESS',-36,18,180000000,'HIGH',-44),
 ('Mobile Health Check-up Day 2026','Fund a weekend mobile clinic providing screening, blood-pressure and diabetes checks, medicine guidance, and referrals for 300 elderly residents in rural Hoa Vang.','minh.tran@mgmgives.example','APPROVED',8,42,90000000,'HIGH',-14),
 ('Green Office: 1,000 Trees for Da Nang','Plant native trees along school grounds and community roads, including seedlings, soil preparation, protective stakes, and a six-month volunteer care schedule.','huy.le@mgmgives.example','IN_PROGRESS',-21,39,75000000,'NORMAL',-30),
 ('Tet Care Packages for Elderly Neighbours','Prepare essential-food boxes, warm blankets, and handwritten New Year cards for elderly people living alone across three neighbourhoods.','thao.vo@mgmgives.example','COMPLETED',-210,-178,65000000,'NORMAL',-224)
)
INSERT INTO campaigns(title,description,user_id,status,start_date,end_date,target,priority,approved_at,approved_by,accepts_money,accepts_goods,donation_method,created_at,updated_at)
SELECT seed.title,seed.description,owner_user.id,seed.status_value::campaign_status,
 NOW()+make_interval(days=>seed.start_delta),NOW()+make_interval(days=>seed.end_delta),seed.target_value,
 seed.priority_value::campaign_priority,NOW()+make_interval(days=>seed.created_delta+5),
 (SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1),
 TRUE,TRUE,'MANUAL_QR',NOW()+make_interval(days=>seed.created_delta),NOW()-INTERVAL '1 day'
FROM seed JOIN users owner_user ON owner_user.email=seed.owner_email
WHERE NOT EXISTS(SELECT 1 FROM campaigns c WHERE c.title=seed.title);

UPDATE campaigns SET result_posted=TRUE, final_amount_raised=70200000,
 result_summary='<h3>What We Achieved</h3><p>The team delivered 150 care packages to elderly neighbours across three community groups.</p><h3>The Campaign Journey</h3><p>Volunteers sourced staple foods, checked blankets, wrote personal cards, and completed deliveries over two weekends.</p>',
 items_summary='In-kind contributions included 42 warm blankets and 150 handwritten cards.',
 acknowledgements='Thank you to donors, volunteers, and community coordinators for making every visit personal.',
 task_summary='The team packed 150 boxes, mapped delivery routes, contacted recipients, and recorded every handover.',
 result_published_by=(SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1),
 result_published_at=NOW()-INTERVAL '170 days',final_donor_count=11,final_volunteer_count=9
WHERE title='Tet Care Packages for Elderly Neighbours';

WITH seed(campaign_title,category_name) AS (VALUES
 ('Back-to-School Kits for Quang Nam','Education'),('Back-to-School Kits for Quang Nam','Children & Youth'),
 ('Mobile Health Check-up Day 2026','Healthcare'),('Mobile Health Check-up Day 2026','Elderly Care'),
 ('Green Office: 1,000 Trees for Da Nang','Environmental Conservation'),('Green Office: 1,000 Trees for Da Nang','Community Development'),
 ('Tet Care Packages for Elderly Neighbours','Elderly Care'),('Tet Care Packages for Elderly Neighbours','Poverty Alleviation')
)
INSERT INTO campaign_categories(campaign_id,category_id)
SELECT c.id,cat.id FROM seed JOIN campaigns c ON c.title=seed.campaign_title JOIN categories cat ON LOWER(cat.name)=LOWER(seed.category_name)
ON CONFLICT(campaign_id,category_id) DO NOTHING;

WITH seed(campaign_title,url,caption) AS (VALUES
 ('MGM Books & Warmth 2026','https://images.unsplash.com/photo-1509099836639-18ba1795216d?auto=format&fit=crop&w=1400&q=80','Children learning together'),
 ('Clean Water for Central Vietnam','https://images.unsplash.com/photo-1541919329513-35f7af297129?auto=format&fit=crop&w=1400&q=80','A clean community water source'),
 ('Mid-Autumn Festival Smiles 2025','https://images.unsplash.com/photo-1488521787991-ed7bbaae773c?auto=format&fit=crop&w=1400&q=80','A joyful community activity'),
 ('Back-to-School Kits for Quang Nam','https://images.unsplash.com/photo-1497633762265-9d179a990aa6?auto=format&fit=crop&w=1400&q=80','School supplies ready for a new term'),
 ('Mobile Health Check-up Day 2026','https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=1400&q=80','A community healthcare team'),
 ('Green Office: 1,000 Trees for Da Nang','https://images.unsplash.com/photo-1542601906990-b4d3fb778b09?auto=format&fit=crop&w=1400&q=80','Planting a young tree'),
 ('Tet Care Packages for Elderly Neighbours','https://images.unsplash.com/photo-1544006659-f0b21884ce1d?auto=format&fit=crop&w=1400&q=80','Warm companionship for elderly neighbours')
)
INSERT INTO campaign_medias(campaign_id,url,media_type,created_at,is_cover,caption,display_order,context)
SELECT c.id,seed.url,'IMAGE',c.created_at+INTERVAL '1 day',TRUE,seed.caption,0,'CAMPAIGN'
FROM seed JOIN campaigns c ON c.title=seed.campaign_title
WHERE NOT EXISTS(SELECT 1 FROM campaign_medias m WHERE m.campaign_id=c.id AND m.url=seed.url);

WITH seed(demo_key,email,campaign_title,type_value,amount_value,detail_value,anonymous_value,days_ago) AS (VALUES
 ('demo-v44-01','an.nguyen@mgmgives.example','MGM Books & Warmth 2026','MONEY',5000000,NULL,FALSE,24),
 ('demo-v44-02','minh.tran@mgmgives.example','MGM Books & Warmth 2026','MONEY',8500000,NULL,FALSE,21),
 ('demo-v44-03','lan.pham@mgmgives.example','MGM Books & Warmth 2026','GOODS',0,'40 storybooks and 20 geometry sets',FALSE,18),
 ('demo-v44-04','huy.le@mgmgives.example','MGM Books & Warmth 2026','MONEY',12000000,NULL,TRUE,14),
 ('demo-v44-05','mai.dang@mgmgives.example','MGM Books & Warmth 2026','GOODS',0,'25 insulated children''s jackets',FALSE,8),
 ('demo-v44-06','bao.ly@mgmgives.example','MGM Books & Warmth 2026','MONEY',6500000,NULL,FALSE,4),
 ('demo-v44-07','quang.do@mgmgives.example','Clean Water for Central Vietnam','MONEY',15000000,NULL,FALSE,45),
 ('demo-v44-08','linh.bui@mgmgives.example','Clean Water for Central Vietnam','MONEY',10000000,NULL,FALSE,39),
 ('demo-v44-09','nam.hoang@mgmgives.example','Clean Water for Central Vietnam','MONEY',25000000,NULL,TRUE,31),
 ('demo-v44-10','thao.vo@mgmgives.example','Clean Water for Central Vietnam','MONEY',7500000,NULL,FALSE,25),
 ('demo-v44-11','phuc.truong@mgmgives.example','Clean Water for Central Vietnam','MONEY',18000000,NULL,FALSE,17),
 ('demo-v44-12','trang.ngo@mgmgives.example','Clean Water for Central Vietnam','MONEY',12000000,NULL,FALSE,7),
 ('demo-v44-13','an.nguyen@mgmgives.example','Mid-Autumn Festival Smiles 2025','MONEY',8000000,NULL,FALSE,304),
 ('demo-v44-14','minh.tran@mgmgives.example','Mid-Autumn Festival Smiles 2025','MONEY',10000000,NULL,FALSE,301),
 ('demo-v44-15','lan.pham@mgmgives.example','Mid-Autumn Festival Smiles 2025','GOODS',0,'180 children''s books',FALSE,298),
 ('demo-v44-16','huy.le@mgmgives.example','Mid-Autumn Festival Smiles 2025','MONEY',15000000,NULL,TRUE,295),
 ('demo-v44-17','thao.vo@mgmgives.example','Mid-Autumn Festival Smiles 2025','GOODS',0,'36 cartons of milk',FALSE,292),
 ('demo-v44-18','quang.do@mgmgives.example','Mid-Autumn Festival Smiles 2025','MONEY',20000000,NULL,FALSE,289),
 ('demo-v44-19','linh.bui@mgmgives.example','Back-to-School Kits for Quang Nam','MONEY',18000000,NULL,FALSE,32),
 ('demo-v44-20','nam.hoang@mgmgives.example','Back-to-School Kits for Quang Nam','MONEY',25000000,NULL,FALSE,27),
 ('demo-v44-21','mai.dang@mgmgives.example','Back-to-School Kits for Quang Nam','GOODS',0,'60 reusable water bottles',FALSE,23),
 ('demo-v44-22','phuc.truong@mgmgives.example','Back-to-School Kits for Quang Nam','MONEY',32000000,NULL,TRUE,19),
 ('demo-v44-23','trang.ngo@mgmgives.example','Back-to-School Kits for Quang Nam','MONEY',16500000,NULL,FALSE,12),
 ('demo-v44-24','bao.ly@mgmgives.example','Back-to-School Kits for Quang Nam','MONEY',21000000,NULL,FALSE,5),
 ('demo-v44-25','an.nguyen@mgmgives.example','Mobile Health Check-up Day 2026','MONEY',7500000,NULL,FALSE,5),
 ('demo-v44-26','quang.do@mgmgives.example','Mobile Health Check-up Day 2026','MONEY',12000000,NULL,FALSE,3),
 ('demo-v44-27','trang.ngo@mgmgives.example','Mobile Health Check-up Day 2026','MONEY',5000000,NULL,FALSE,1),
 ('demo-v44-28','minh.tran@mgmgives.example','Green Office: 1,000 Trees for Da Nang','MONEY',10000000,NULL,FALSE,18),
 ('demo-v44-29','lan.pham@mgmgives.example','Green Office: 1,000 Trees for Da Nang','GOODS',0,'80 bamboo stakes and gardening gloves',FALSE,15),
 ('demo-v44-30','huy.le@mgmgives.example','Green Office: 1,000 Trees for Da Nang','MONEY',15000000,NULL,FALSE,11),
 ('demo-v44-31','nam.hoang@mgmgives.example','Green Office: 1,000 Trees for Da Nang','MONEY',8500000,NULL,TRUE,6),
 ('demo-v44-32','bao.ly@mgmgives.example','Green Office: 1,000 Trees for Da Nang','MONEY',6000000,NULL,FALSE,2),
 ('demo-v44-33','an.nguyen@mgmgives.example','Tet Care Packages for Elderly Neighbours','MONEY',10000000,NULL,FALSE,205),
 ('demo-v44-34','linh.bui@mgmgives.example','Tet Care Packages for Elderly Neighbours','MONEY',15000000,NULL,FALSE,198),
 ('demo-v44-35','mai.dang@mgmgives.example','Tet Care Packages for Elderly Neighbours','GOODS',0,'42 warm blankets',FALSE,194),
 ('demo-v44-36','phuc.truong@mgmgives.example','Tet Care Packages for Elderly Neighbours','MONEY',20200000,NULL,FALSE,188),
 ('demo-v44-37','trang.ngo@mgmgives.example','Tet Care Packages for Elderly Neighbours','GOODS',0,'150 handwritten New Year cards',FALSE,184),
 ('demo-v44-38','bao.ly@mgmgives.example','Tet Care Packages for Elderly Neighbours','MONEY',25000000,NULL,TRUE,180)
)
INSERT INTO donations(user_id,campaign_id,type,amount,detail,is_anonymous,status,transaction_id,confirmed_by,confirmed_at,created_at,updated_at,idempotency_key,transaction_description,is_message_hidden)
SELECT u.id,c.id,seed.type_value::donation_type,seed.amount_value,seed.detail_value,seed.anonymous_value,'SUCCESSFUL',
 CASE WHEN seed.type_value='MONEY' THEN 'DEMO-'||seed.demo_key END,
 (SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1),NOW()-make_interval(days=>seed.days_ago),
 NOW()-make_interval(days=>seed.days_ago),NOW()-make_interval(days=>seed.days_ago),seed.demo_key,
 CASE WHEN seed.type_value='MONEY' THEN 'MGMGIVES '||UPPER(REPLACE(seed.demo_key,'-','')) END,FALSE
FROM seed JOIN users u ON u.email=seed.email JOIN campaigns c ON c.title=seed.campaign_title
ON CONFLICT(idempotency_key) DO NOTHING;

WITH seed(campaign_title,email,role_value,days_ago) AS (VALUES
 ('MGM Books & Warmth 2026','lan.pham@mgmgives.example','CAMPAIGN_ADMIN',27),('MGM Books & Warmth 2026','an.nguyen@mgmgives.example','VOLUNTEER',24),('MGM Books & Warmth 2026','mai.dang@mgmgives.example','VOLUNTEER',17),
 ('Clean Water for Central Vietnam','minh.tran@mgmgives.example','CAMPAIGN_ADMIN',50),('Clean Water for Central Vietnam','quang.do@mgmgives.example','VOLUNTEER',43),('Clean Water for Central Vietnam','nam.hoang@mgmgives.example','VOLUNTEER',36),
 ('Mid-Autumn Festival Smiles 2025','thao.vo@mgmgives.example','CAMPAIGN_ADMIN',310),('Mid-Autumn Festival Smiles 2025','an.nguyen@mgmgives.example','VOLUNTEER',303),('Mid-Autumn Festival Smiles 2025','huy.le@mgmgives.example','VOLUNTEER',296),
 ('Back-to-School Kits for Quang Nam','lan.pham@mgmgives.example','CAMPAIGN_ADMIN',43),('Back-to-School Kits for Quang Nam','linh.bui@mgmgives.example','VOLUNTEER',34),('Back-to-School Kits for Quang Nam','mai.dang@mgmgives.example','VOLUNTEER',28),
 ('Mobile Health Check-up Day 2026','minh.tran@mgmgives.example','CAMPAIGN_ADMIN',13),('Mobile Health Check-up Day 2026','an.nguyen@mgmgives.example','VOLUNTEER',5),
 ('Green Office: 1,000 Trees for Da Nang','huy.le@mgmgives.example','CAMPAIGN_ADMIN',29),('Green Office: 1,000 Trees for Da Nang','nam.hoang@mgmgives.example','VOLUNTEER',19),('Green Office: 1,000 Trees for Da Nang','bao.ly@mgmgives.example','VOLUNTEER',9),
 ('Tet Care Packages for Elderly Neighbours','thao.vo@mgmgives.example','CAMPAIGN_ADMIN',220),('Tet Care Packages for Elderly Neighbours','linh.bui@mgmgives.example','VOLUNTEER',202),('Tet Care Packages for Elderly Neighbours','phuc.truong@mgmgives.example','VOLUNTEER',195)
)
INSERT INTO campaign_members(campaign_id,user_id,role_in_campaign,joined_at)
SELECT c.id,u.id,seed.role_value::campaign_member_role,NOW()-make_interval(days=>seed.days_ago)
FROM seed JOIN campaigns c ON c.title=seed.campaign_title JOIN users u ON u.email=seed.email
ON CONFLICT(campaign_id,user_id) DO NOTHING;

WITH seed(campaign_title,title,content,days_ago) AS (VALUES
 ('MGM Books & Warmth 2026','First 120 learning kits are ready','<p>Volunteers completed the first packing session with notebooks, pencils, rulers, and reading books.</p>',12),
 ('Clean Water for Central Vietnam','Filtration equipment supplier confirmed','<p>The selected units can be serviced locally and include a maintenance handover for commune technicians.</p>',28),
 ('Clean Water for Central Vietnam','Site survey completed','<p>Both sites passed water-flow and access checks. Foundation work can now begin.</p>',10),
 ('Back-to-School Kits for Quang Nam','Uniform measurements collected','<p>Teachers completed the pupil size list and the supplier has started the first uniform batch.</p>',17),
 ('Mobile Health Check-up Day 2026','Volunteer medical team confirmed','<p>Two doctors, four nurses, and six support volunteers have confirmed for the clinic weekend.</p>',2),
 ('Green Office: 1,000 Trees for Da Nang','First 300 native seedlings reserved','<p>The nursery reserved shade and flowering species selected for the planting sites.</p>',9),
 ('Tet Care Packages for Elderly Neighbours','All 150 care packages delivered','<p>Delivery teams completed the final route and recorded every handover.</p>',176)
)
INSERT INTO announcements(campaign_id,title,content,created_by,published_at,created_at,updated_at,likes_count,replies_count)
SELECT c.id,seed.title,seed.content,c.user_id,NOW()-make_interval(days=>seed.days_ago),NOW()-make_interval(days=>seed.days_ago),NOW()-make_interval(days=>seed.days_ago),2+(seed.days_ago%7),seed.days_ago%3
FROM seed JOIN campaigns c ON c.title=seed.campaign_title
WHERE NOT EXISTS(SELECT 1 FROM announcements a WHERE a.campaign_id=c.id AND a.title=seed.title);

WITH seed(campaign_title,title,description,status_value,due_delta,position_value) AS (VALUES
 ('MGM Books & Warmth 2026','Verify donated books','Check condition, reading level, and quantity before packing.','DONE',-10,1),
 ('MGM Books & Warmth 2026','Pack learning kits','Prepare 320 labelled kits using the school class lists.','IN_PROGRESS',5,1),
 ('Clean Water for Central Vietnam','Complete installation site survey','Confirm access, water source, and concrete-base requirements.','DONE',-12,1),
 ('Clean Water for Central Vietnam','Prepare technician training guide','Document filter replacement and monthly maintenance checks.','IN_PROGRESS',9,1),
 ('Back-to-School Kits for Quang Nam','Confirm pupil uniform sizes','Reconcile class lists with the supplier order sheet.','DONE',-4,1),
 ('Back-to-School Kits for Quang Nam','Assemble stationery packs','Pack notebooks, pens, rulers, and geometry sets by grade.','IN_PROGRESS',7,1),
 ('Mobile Health Check-up Day 2026','Prepare screening stations','Plan patient flow for registration, vital signs, consultation, and referral.','TODO',16,1),
 ('Green Office: 1,000 Trees for Da Nang','Map planting locations','Mark safe planting points with schools and local coordinators.','DONE',-3,1),
 ('Green Office: 1,000 Trees for Da Nang','Create six-month care rota','Assign watering and survival-check shifts after planting day.','IN_PROGRESS',12,1),
 ('Tet Care Packages for Elderly Neighbours','Pack 150 care boxes','Quality-check food dates and add one handwritten card per box.','DONE',-185,1)
)
INSERT INTO campaign_tasks(campaign_id,title,description,status,due_date,created_by,created_at,updated_at,position,version,is_archived)
SELECT c.id,seed.title,seed.description,seed.status_value::task_status,NOW()+make_interval(days=>seed.due_delta),c.user_id,c.created_at+INTERVAL '3 days',NOW()-INTERVAL '1 day',seed.position_value,0,FALSE
FROM seed JOIN campaigns c ON c.title=seed.campaign_title
WHERE NOT EXISTS(SELECT 1 FROM campaign_tasks t WHERE t.campaign_id=c.id AND t.title=seed.title);

WITH seed(campaign_title,amount_value,description,days_ago) AS (VALUES
 ('Clean Water for Central Vietnam',32000000,'Deposit for two community filtration units',20),
 ('Clean Water for Central Vietnam',6800000,'Site survey, water testing, and transport',11),
 ('Mid-Autumn Festival Smiles 2025',24500000,'Mooncakes, lantern materials, and gift-pack supplies',291),
 ('Back-to-School Kits for Quang Nam',46200000,'First supplier payment for backpacks and uniforms',16),
 ('Green Office: 1,000 Trees for Da Nang',18500000,'Reservation deposit for 300 native seedlings',8),
 ('Tet Care Packages for Elderly Neighbours',39800000,'Rice, cooking oil, and essential-food package supplies',187)
)
INSERT INTO campaign_spendings(campaign_id,amount,description,spent_at,created_by,created_at,updated_at)
SELECT c.id,seed.amount_value,seed.description,CURRENT_DATE-seed.days_ago,c.user_id,NOW()-make_interval(days=>seed.days_ago),NOW()-make_interval(days=>seed.days_ago)
FROM seed JOIN campaigns c ON c.title=seed.campaign_title
WHERE NOT EXISTS(SELECT 1 FROM campaign_spendings s WHERE s.campaign_id=c.id AND s.description=seed.description);
