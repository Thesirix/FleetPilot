-- Reference tariff negotiated with the garage networks (2026 prices, excluding tax).
-- Quotes are compared line by line with these values: part prices, standard labour times and regional hourly rates.

insert into part_reference (code, label, family, price_car, price_van, price_truck) values
-- brakes
('PLAQ-AV',      'Plaquettes de frein avant (jeu)',                  'BRAKES',      45.00,   58.00,  165.00),
('PLAQ-AR',      'Plaquettes de frein arrière (jeu)',                'BRAKES',      38.00,   52.00,  150.00),
('DISQ-AV',      'Disques de frein avant (paire)',                   'BRAKES',      95.00,  130.00,  380.00),
('DISQ-AR',      'Disques de frein arrière (paire)',                 'BRAKES',      80.00,  115.00,  340.00),
('LIQ-FREIN',    'Liquide de frein DOT 4 (1 L)',                     'BRAKES',      12.00,   12.00,   14.00),
-- service
('FILT-HUILE',   'Filtre à huile',                                   'SERVICE',     12.00,   15.00,   45.00),
('FILT-AIR',     'Filtre à air',                                     'SERVICE',     18.00,   24.00,   85.00),
('FILT-HAB',     'Filtre d''habitacle',                              'SERVICE',     16.00,   20.00,   40.00),
('FILT-GO',      'Filtre à gazole',                                  'SERVICE',     32.00,   38.00,   95.00),
('HUILE-5W30',   'Huile moteur 5W30 (1 L)',                          'SERVICE',     11.00,   11.00,    9.00),
('BOUGIE',       'Bougie d''allumage',                               'SERVICE',      9.00,   10.00,   12.00),
('KIT-DIST',     'Kit de distribution avec pompe à eau',             'SERVICE',    260.00,  320.00,  690.00),
('ADBLUE',       'AdBlue (bidon de 10 L)',                           'SERVICE',     14.00,   14.00,   14.00),
-- tyres
('PNEU',         'Pneumatique (unité)',                              'TYRES',       95.00,  120.00,  420.00),
('VALVE',        'Valve de pneumatique',                             'TYRES',        3.00,    3.00,    8.00),
-- electrical
('BATT-12V',     'Batterie 12 V',                                    'ELECTRICAL', 125.00,  150.00,  290.00),
('ALTERN',       'Alternateur (échange standard)',                   'ELECTRICAL', 340.00,  390.00,  780.00),
('DEMARR',       'Démarreur (échange standard)',                     'ELECTRICAL', 290.00,  330.00,  690.00),
('AMPOULE-H7',   'Ampoule H7',                                       'ELECTRICAL',   9.00,    9.00,   12.00),
('BALAIS-ESS',   'Balais d''essuie-glace avant (paire)',             'ELECTRICAL',  28.00,   32.00,   55.00),
('CABLE-T2',     'Câble de recharge type 2',                         'ELECTRICAL', 220.00,  220.00,  260.00),
-- suspension and steering
('AMORT-AV',     'Amortisseur avant (unité)',                        'SUSPENSION',  75.00,   95.00,  260.00),
('AMORT-AR',     'Amortisseur arrière (unité)',                      'SUSPENSION',  65.00,   85.00,  240.00),
('BIEL-STAB',    'Biellette de barre stabilisatrice',                'SUSPENSION',  28.00,   34.00,   90.00),
('ROTULE',       'Rotule de direction',                              'SUSPENSION',  32.00,   40.00,  110.00),
-- powertrain and exhaust
('KIT-EMBR',     'Kit d''embrayage',                                 'POWERTRAIN', 290.00,  380.00, 1250.00),
('FAP',          'Filtre à particules',                              'POWERTRAIN', 780.00,  920.00, 2400.00),
('VANNE-EGR',    'Vanne EGR',                                        'POWERTRAIN', 210.00,  240.00,  480.00),
('INJECTEUR',    'Injecteur (échange standard)',                     'POWERTRAIN', 260.00,  290.00,  720.00),
('SONDE-LAMBDA', 'Sonde lambda',                                     'POWERTRAIN',  85.00,   95.00,  180.00),
-- air conditioning
('GAZ-CLIM',     'Gaz réfrigérant R1234yf (100 g)',                  'CLIMATE',     18.00,   18.00,   18.00),
('COMPR-CLIM',   'Compresseur de climatisation',                     'CLIMATE',    580.00,  640.00, 1300.00),
('FILT-DESHY',   'Filtre déshydrateur',                              'CLIMATE',     45.00,   50.00,   90.00),
-- bodywork and glass
('PARE-BRISE',   'Pare-brise',                                       'BODYWORK',   420.00,  480.00,  950.00),
('RETRO-EXT',    'Rétroviseur extérieur',                            'BODYWORK',   140.00,  160.00,  320.00),
('PARE-CHOC-AV', 'Pare-chocs avant',                                 'BODYWORK',   280.00,  340.00,  780.00),
('PHARE-AV',     'Optique de phare avant',                           'BODYWORK',   260.00,  310.00,  520.00),
('PEINT-ELEM',   'Ingrédients de peinture (par élément)',            'BODYWORK',    95.00,  110.00,  180.00);

insert into labour_operation (code, label, labour_category, hours_car, hours_van, hours_truck) values
('VIDANGE',        'Vidange moteur et remplacement du filtre à huile',        'MECH',  0.50, 0.60, 1.20),
('REVISION',       'Révision selon le plan d''entretien du constructeur',     'MECH',  1.00, 1.20, 2.50),
('FREIN-AV',       'Remplacement des plaquettes avant',                       'MECH',  0.80, 0.90, 1.60),
('FREIN-AV-DISQ',  'Remplacement des disques et plaquettes avant',            'MECH',  1.30, 1.50, 2.80),
('FREIN-AR-DISQ',  'Remplacement des disques et plaquettes arrière',          'MECH',  1.20, 1.40, 2.60),
('PURGE-FREIN',    'Purge du circuit de freinage',                            'MECH',  0.60, 0.70, 1.20),
('DISTRIB',        'Remplacement du kit de distribution',                     'MECH',  3.50, 4.00, 6.00),
('EMBRAY',         'Remplacement de l''embrayage',                            'MECH',  5.00, 5.80, 9.00),
('AMORT-AV',       'Remplacement des amortisseurs avant (paire)',             'MECH',  1.60, 1.80, 3.00),
('AMORT-AR',       'Remplacement des amortisseurs arrière (paire)',           'MECH',  1.20, 1.40, 2.60),
('PNEU-MONT-2',    'Montage et équilibrage de deux pneumatiques',             'MECH',  0.60, 0.70, 1.20),
('PNEU-MONT-4',    'Montage et équilibrage de quatre pneumatiques',           'MECH',  1.20, 1.40, 2.40),
('GEOMETRIE',      'Contrôle et réglage de la géométrie',                     'MECH',  0.80, 0.80, 1.50),
('FAP',            'Remplacement du filtre à particules',                     'MECH',  2.00, 2.40, 4.00),
('EGR',            'Remplacement de la vanne EGR',                            'MECH',  2.20, 2.50, 3.50),
('INJECT',         'Remplacement des injecteurs (jeu complet)',               'MECH',  2.50, 2.80, 4.50),
('BATTERIE',       'Remplacement de la batterie',                             'ELEC',  0.30, 0.40, 0.80),
('DIAG',           'Diagnostic électronique',                                 'ELEC',  0.50, 0.50, 1.00),
('ALTERN',         'Remplacement de l''alternateur',                          'ELEC',  1.50, 1.80, 3.00),
('DEMARR',         'Remplacement du démarreur',                               'ELEC',  1.30, 1.60, 2.80),
('CLIM',           'Contrôle et recharge de la climatisation',                'ELEC',  0.80, 0.90, 1.50),
('COMPR-CLIM',     'Remplacement du compresseur de climatisation',            'ELEC',  2.50, 3.00, 4.50),
('TACHY',          'Contrôle périodique du chronotachygraphe',                'ELEC',  1.50, 1.50, 1.50),
('EV-BATT',        'Contrôle de la batterie de traction',                     'ELEC',  1.00, 1.00, 1.50),
('PARE-BRISE',     'Remplacement du pare-brise',                              'BODY',  1.50, 1.80, 3.00),
('PARE-CHOC',      'Dépose et repose du pare-chocs avant',                    'BODY',  1.20, 1.40, 2.50),
('DEBOSSELAGE',    'Débosselage d''un élément',                               'BODY',  1.50, 1.80, 3.00),
('RETRO',          'Remplacement d''un rétroviseur extérieur',                'BODY',  0.50, 0.60, 1.00),
('PHARE',          'Remplacement d''une optique de phare',                    'BODY',  0.60, 0.70, 1.20),
('PEINTURE',       'Peinture d''un élément',                                  'PAINT', 2.50, 2.80, 4.00);

insert into labour_rate (region, labour_category, hourly_rate, truck_hourly_rate) values
('IDF', 'MECH', 86.00, 108.00), ('IDF', 'ELEC', 92.00, 115.00), ('IDF', 'BODY', 84.00, 104.00), ('IDF', 'PAINT', 88.00, 110.00),
('PAC', 'MECH', 72.00,  92.00), ('PAC', 'ELEC', 78.00,  98.00), ('PAC', 'BODY', 70.00,  88.00), ('PAC', 'PAINT', 74.00,  93.00),
('ARA', 'MECH', 74.00,  94.00), ('ARA', 'ELEC', 80.00, 100.00), ('ARA', 'BODY', 72.00,  90.00), ('ARA', 'PAINT', 76.00,  95.00),
('HDF', 'MECH', 68.00,  88.00), ('HDF', 'ELEC', 74.00,  94.00), ('HDF', 'BODY', 66.00,  84.00), ('HDF', 'PAINT', 70.00,  88.00),
('NAQ', 'MECH', 69.00,  89.00), ('NAQ', 'ELEC', 75.00,  95.00), ('NAQ', 'BODY', 67.00,  85.00), ('NAQ', 'PAINT', 71.00,  89.00),
('OCC', 'MECH', 70.00,  90.00), ('OCC', 'ELEC', 76.00,  96.00), ('OCC', 'BODY', 68.00,  86.00), ('OCC', 'PAINT', 72.00,  90.00),
('PDL', 'MECH', 68.00,  88.00), ('PDL', 'ELEC', 74.00,  94.00), ('PDL', 'BODY', 66.00,  84.00), ('PDL', 'PAINT', 70.00,  88.00),
('GES', 'MECH', 70.00,  90.00), ('GES', 'ELEC', 76.00,  96.00), ('GES', 'BODY', 68.00,  86.00), ('GES', 'PAINT', 72.00,  90.00);
