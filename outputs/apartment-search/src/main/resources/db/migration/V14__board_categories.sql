ALTER TABLE board_post ADD COLUMN category VARCHAR(12) NOT NULL DEFAULT 'FREE'
 CHECK (category IN ('FREE','QUESTION','INFO'));
CREATE INDEX idx_board_post_category_hidden_id ON board_post(category,hidden,id);
