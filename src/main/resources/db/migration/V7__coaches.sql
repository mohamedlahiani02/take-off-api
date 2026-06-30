CREATE TABLE coaches (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name            TEXT        NOT NULL,
    last_name             TEXT        NOT NULL,
    role_title            TEXT        NOT NULL,
    bio                   TEXT        NOT NULL,
    specs                 TEXT[]      NOT NULL DEFAULT '{}',
    achievements          TEXT[]      NOT NULL DEFAULT '{}',
    photo_url             TEXT,
    display_order         INT         NOT NULL DEFAULT 0,
    show_on_padel_preview BOOLEAN     NOT NULL DEFAULT FALSE,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    activity              TEXT        NOT NULL DEFAULT 'PADEL' CHECK (activity IN ('PADEL','PILATES')),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Padel coaches (seeded from prototype JS _coachesData)
INSERT INTO coaches (first_name, last_name, role_title, bio, specs, achievements, display_order, show_on_padel_preview, activity) VALUES
(
    'Ahmed', 'Ben Salah',
    'Head Padel Coach',
    'Ahmed brings over 12 years of professional padel experience to Take Off. A former national champion, he has coached players at all levels from beginners to elite competitors.',
    ARRAY['Técnica y táctica avanzada', 'Formación de jugadores élite', 'Análisis de juego'],
    ARRAY['Campeón Nacional Tunecino 2015, 2017', 'Certificación Nivel 3 FIP', 'Entrenador del Año 2020'],
    1, TRUE, 'PADEL'
),
(
    'Karim', 'Mansouri',
    'Senior Padel Coach',
    'Karim specialises in technical development and biomechanics. His systematic approach to skill-building has helped dozens of players achieve rapid improvement.',
    ARRAY['Biomecánica del movimiento', 'Desarrollo técnico', 'Acondicionamiento físico'],
    ARRAY['Ex-jugador profesional del circuito WPT', 'Certificación Nivel 2 FIP', 'Especialista en metodología de enseñanza'],
    2, TRUE, 'PADEL'
),
(
    'Sami', 'Trabelsi',
    'Padel Coach & Fitness Trainer',
    'Sami combines padel coaching with sports conditioning, helping players build the physical attributes needed to excel on court.',
    ARRAY['Preparación física específica', 'Prevención de lesiones', 'Técnica de golpeos'],
    ARRAY['Licenciado en Ciencias del Deporte', 'Certificación Nivel 1 FIP', 'Especialista en Acondicionamiento Deportivo'],
    3, TRUE, 'PADEL'
),
(
    'Youssef', 'Chaabani',
    'Padel Coach',
    'Youssef has a passion for developing junior and beginner players, creating a fun and structured learning environment that builds confidence and skill simultaneously.',
    ARRAY['Iniciación al pádel', 'Programa junior', 'Táctica de dobles'],
    ARRAY['Especialista en desarrollo juvenil', 'Certificación Nivel 1 FIP', '5 años de experiencia en enseñanza'],
    4, FALSE, 'PADEL'
),
(
    'Mehdi', 'Jebali',
    'Advanced Padel Coach',
    'Mehdi focuses on advanced competition preparation and strategy. He works primarily with competitive players looking to elevate their tournament performance.',
    ARRAY['Preparación para torneos', 'Estrategia competitiva', 'Mentalidad ganadora'],
    ARRAY['Finalista Campeonato Nacional 2018', 'Certificación Nivel 2 FIP', 'Entrenador de equipos de competición'],
    5, FALSE, 'PADEL'
),
(
    'Fares', 'Ounissi',
    'Padel Coach',
    'Fares brings an energetic and motivating coaching style to all sessions. His background in sports psychology helps players overcome mental barriers on court.',
    ARRAY['Psicología deportiva', 'Técnica de revés', 'Juego en red'],
    ARRAY['Máster en Psicología Deportiva', 'Certificación Nivel 1 FIP', 'Especialista en rendimiento mental'],
    6, FALSE, 'PADEL'
),
(
    'Nizar', 'Belhaj',
    'Padel & Tennis Coach',
    'Nizar''s dual expertise in padel and tennis gives his students unique technical insights. He is particularly skilled at helping tennis players transition to padel.',
    ARRAY['Transición tenis-pádel', 'Técnica de volea', 'Coordinación y timing'],
    ARRAY['Ex-jugador de tenis de élite', 'Certificación Doble (Tenis + Pádel)', 'Especialista en transferencia técnica'],
    7, FALSE, 'PADEL'
),
(
    'Amine', 'Khelifi',
    'Padel Coach',
    'Amine is our youngest coach but brings fresh competitive energy and modern training methodologies, having recently competed at the international level.',
    ARRAY['Técnicas modernas de juego', 'Defensa y contrataque', 'Acondicionamiento juvenil'],
    ARRAY['Representante internacional Sub-23', 'Certificación Nivel 1 FIP', 'Especialista en nuevas metodologías'],
    8, FALSE, 'PADEL'
);

-- Pilates instructors (seeded from prototype JS _instrData)
INSERT INTO coaches (first_name, last_name, role_title, bio, specs, achievements, display_order, show_on_padel_preview, activity) VALUES
(
    'Leila', 'Bouaziz',
    'Head Pilates Instructor',
    'Leila is a certified Pilates instructor with over 10 years of experience in mat and reformer Pilates. She specialises in helping athletes improve their core strength and flexibility.',
    ARRAY['Pilates de reformer', 'Pilates para deportistas', 'Entrenamiento de fuerza del core'],
    ARRAY['Certificada STOTT Pilates Nivel Avanzado', 'Especialista en Pilates para lesiones', '10+ años de experiencia'],
    1, FALSE, 'PILATES'
),
(
    'Sara', 'Hamdi',
    'Pilates & Yoga Instructor',
    'Sara combines Pilates principles with yoga mindfulness to create holistic sessions that improve both physical performance and mental wellbeing.',
    ARRAY['Pilates-Yoga fusión', 'Mindfulness y meditación', 'Flexibilidad y movilidad'],
    ARRAY['Certificada en Pilates Clásico', 'Instructora de Yoga RYT-200', 'Especialista en bienestar holístico'],
    2, FALSE, 'PILATES'
),
(
    'Rim', 'Ayari',
    'Pilates Instructor',
    'Rim focuses on postural correction and rehabilitation Pilates, helping clients recover from injuries and build sustainable movement patterns.',
    ARRAY['Pilates de rehabilitación', 'Corrección postural', 'Movimiento funcional'],
    ARRAY['Fisioterapeuta certificada', 'Especialización en Pilates clínico', 'Experta en corrección postural'],
    3, FALSE, 'PILATES'
);
