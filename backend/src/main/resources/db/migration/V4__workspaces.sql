CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_workspaces_owner_name
    ON workspaces(owner_id, LOWER(name));
CREATE INDEX idx_workspaces_owner ON workspaces(owner_id, created_at);

INSERT INTO workspaces (id, owner_id, name, created_at, updated_at)
SELECT gen_random_uuid(), id, 'Default Workspace', created_at, NOW()
FROM app_users;

ALTER TABLE documents ADD COLUMN workspace_id UUID;
UPDATE documents document
SET workspace_id = workspace.id
FROM workspaces workspace
WHERE workspace.owner_id = document.owner_id
  AND workspace.name = 'Default Workspace';
ALTER TABLE documents ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE documents
    ADD CONSTRAINT fk_documents_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;
CREATE INDEX idx_documents_workspace_created
    ON documents(workspace_id, created_at DESC);

ALTER TABLE chat_sessions ADD COLUMN workspace_id UUID;
UPDATE chat_sessions session
SET workspace_id = workspace.id
FROM workspaces workspace
WHERE workspace.owner_id = session.user_id
  AND workspace.name = 'Default Workspace';
ALTER TABLE chat_sessions ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE chat_sessions
    ADD CONSTRAINT fk_chat_sessions_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;
CREATE INDEX idx_sessions_workspace_user_updated
    ON chat_sessions(workspace_id, user_id, updated_at DESC);
