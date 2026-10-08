-- Question Paper Generator - Stage 1 Schema (XAMPP / MariaDB)

CREATE DATABASE IF NOT EXISTS question_paper_db;
USE question_paper_db;

CREATE TABLE IF NOT EXISTS subjects (
  subject_id INT AUTO_INCREMENT PRIMARY KEY,
  subject_name VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS modules (
  module_id INT AUTO_INCREMENT PRIMARY KEY,
  subject_id INT NOT NULL,
  module_name VARCHAR(100) NOT NULL,
  UNIQUE (subject_id, module_name),
  CONSTRAINT fk_modules_subject
    FOREIGN KEY (subject_id)
    REFERENCES subjects(subject_id)
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS questions (
  question_id INT AUTO_INCREMENT PRIMARY KEY,
  subject_id INT NOT NULL,
  module_id INT NOT NULL,
  question_text TEXT NOT NULL,
  answer_text TEXT NOT NULL,
  marks INT NOT NULL CHECK (marks > 0),
  difficulty VARCHAR(10) NOT NULL,
  CONSTRAINT chk_questions_difficulty
    CHECK (difficulty IN ('Easy','Medium','Hard')),
  CONSTRAINT fk_questions_subject
    FOREIGN KEY (subject_id)
    REFERENCES subjects(subject_id),
  CONSTRAINT fk_questions_module
    FOREIGN KEY (module_id)
    REFERENCES modules(module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS question_papers (
  paper_id INT AUTO_INCREMENT PRIMARY KEY,
  subject_id INT NOT NULL,
  total_marks INT NOT NULL,
  generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_question_papers_subject
    FOREIGN KEY (subject_id)
    REFERENCES subjects(subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS paper_questions (
  paper_question_id INT AUTO_INCREMENT PRIMARY KEY,
  paper_id INT NOT NULL,
  question_id INT NOT NULL,
  question_text TEXT NOT NULL,
  answer_text TEXT NOT NULL,
  marks INT NOT NULL,
  difficulty VARCHAR(10) NOT NULL,
  sequence_no INT NOT NULL,
  UNIQUE (paper_id, question_id),
  CONSTRAINT chk_paper_questions_difficulty
    CHECK (difficulty IN ('Easy','Medium','Hard')),
  CONSTRAINT fk_paper_questions_paper
    FOREIGN KEY (paper_id)
    REFERENCES question_papers(paper_id)
    ON DELETE CASCADE,
  CONSTRAINT fk_paper_questions_question
    FOREIGN KEY (question_id)
    REFERENCES questions(question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS paper_blueprints (
  blueprint_id INT AUTO_INCREMENT PRIMARY KEY,
  paper_id INT NOT NULL UNIQUE,
  subject_id INT NOT NULL,
  CONSTRAINT fk_paper_blueprints_paper
    FOREIGN KEY (paper_id)
    REFERENCES question_papers(paper_id)
    ON DELETE CASCADE,
  CONSTRAINT fk_paper_blueprints_subject
    FOREIGN KEY (subject_id)
    REFERENCES subjects(subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS paper_blueprint_slots (
  slot_id INT AUTO_INCREMENT PRIMARY KEY,
  blueprint_id INT NOT NULL,
  sequence_no INT NOT NULL,
  module_id INT NOT NULL,
  marks INT NOT NULL,
  difficulty VARCHAR(10) NOT NULL,
  UNIQUE (blueprint_id, sequence_no),
  CONSTRAINT chk_paper_blueprint_slots_marks
    CHECK (marks > 0),
  CONSTRAINT chk_paper_blueprint_slots_difficulty
    CHECK (difficulty IN ('Easy','Medium','Hard')),
  CONSTRAINT fk_paper_blueprint_slots_blueprint
    FOREIGN KEY (blueprint_id)
    REFERENCES paper_blueprints(blueprint_id)
    ON DELETE CASCADE,
  CONSTRAINT fk_paper_blueprint_slots_module
    FOREIGN KEY (module_id)
    REFERENCES modules(module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
