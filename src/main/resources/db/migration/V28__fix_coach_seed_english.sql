-- V28 — Fix coach seed data: replace Spanish specs/achievements with English

UPDATE coaches SET
    specs         = ARRAY['Advanced technique & tactics', 'Elite player development', 'Game analysis & video review'],
    achievements  = ARRAY['Tunisian National Champion 2015, 2017', 'FIP Level 3 Certification', 'Coach of the Year 2020']
WHERE first_name = 'Ahmed' AND last_name = 'Ben Salah';

UPDATE coaches SET
    specs         = ARRAY['Movement biomechanics', 'Technical skill development', 'Sports conditioning'],
    achievements  = ARRAY['Former WPT professional circuit player', 'FIP Level 2 Certification', 'Teaching methodology specialist']
WHERE first_name = 'Karim' AND last_name = 'Mansouri';

UPDATE coaches SET
    specs         = ARRAY['Sport-specific physical preparation', 'Injury prevention', 'Stroke technique'],
    achievements  = ARRAY['BSc Sports Science', 'FIP Level 1 Certification', 'Sports Conditioning Specialist']
WHERE first_name = 'Sami' AND last_name = 'Trabelsi';

UPDATE coaches SET
    specs         = ARRAY['Padel initiation', 'Junior development programme', 'Doubles tactics'],
    achievements  = ARRAY['Youth development specialist', 'FIP Level 1 Certification', '5 years teaching experience']
WHERE first_name = 'Youssef' AND last_name = 'Chaabani';

UPDATE coaches SET
    specs         = ARRAY['Tournament preparation', 'Competitive strategy', 'Winning mindset'],
    achievements  = ARRAY['National Championship finalist 2018', 'FIP Level 2 Certification', 'Competition team coach']
WHERE first_name = 'Mehdi' AND last_name = 'Jebali';

UPDATE coaches SET
    specs         = ARRAY['Sports psychology', 'Backhand technique', 'Net game'],
    achievements  = ARRAY['MSc Sports Psychology', 'FIP Level 1 Certification', 'Mental performance specialist']
WHERE first_name = 'Fares' AND last_name = 'Ounissi';

UPDATE coaches SET
    specs         = ARRAY['Tennis-to-padel transition', 'Volley technique', 'Coordination & timing'],
    achievements  = ARRAY['Former elite tennis player', 'Dual certification (Tennis + Padel)', 'Technical transfer specialist']
WHERE first_name = 'Nizar' AND last_name = 'Belhaj';

UPDATE coaches SET
    specs         = ARRAY['Modern game techniques', 'Defence & counter-attack', 'Youth conditioning'],
    achievements  = ARRAY['U-23 international representative', 'FIP Level 1 Certification', 'New methodology specialist']
WHERE first_name = 'Amine' AND last_name = 'Khelifi';

UPDATE coaches SET
    specs         = ARRAY['Reformer Pilates', 'Pilates for athletes', 'Core strength training'],
    achievements  = ARRAY['STOTT Pilates Advanced Certification', 'Pilates for injury rehabilitation specialist', '10+ years experience']
WHERE first_name = 'Leila' AND last_name = 'Bouaziz';

UPDATE coaches SET
    specs         = ARRAY['Pilates-Yoga fusion', 'Mindfulness & meditation', 'Flexibility & mobility'],
    achievements  = ARRAY['Classical Pilates Certified', 'RYT-200 Yoga Instructor', 'Holistic wellness specialist']
WHERE first_name = 'Sara' AND last_name = 'Hamdi';

UPDATE coaches SET
    specs         = ARRAY['Rehabilitation Pilates', 'Postural correction', 'Functional movement'],
    achievements  = ARRAY['Certified physiotherapist', 'Clinical Pilates specialisation', 'Postural correction expert']
WHERE first_name = 'Rim' AND last_name = 'Ayari';
