import { useEffect, useMemo, useState } from "react";
import { Alert, Card, Form } from "react-bootstrap";
import { useGetIndexMembersQuery, useGetIndexesQuery } from "../functions/api";
import { IndexMembersTable } from "../components/IndexMembersTable";
import { Fattore1000Russell1000CompareTable } from "../components/Fattore1000Russell1000CompareTable";

export default function Indexes() {
  const {
    data: indexes,
    isLoading: indexesLoading,
    error: indexesError,
  } = useGetIndexesQuery();

  const [selectedCode, setSelectedCode] = useState<string>("");

  useEffect(() => {
    if (selectedCode) return;
    if (!indexes || indexes.length === 0) return;
    const fat50 = indexes.find((i) => i.code === "FAT50");
    setSelectedCode(fat50?.code || indexes[0].code);
  }, [indexes, selectedCode]);

  const shouldFetchMembers = selectedCode.trim().length > 0;
  const {
    data: members,
    isLoading: membersLoading,
    error: membersError,
  } = useGetIndexMembersQuery(selectedCode, { skip: !shouldFetchMembers });

  const selectedLabel = useMemo(() => {
    if (!indexes) return selectedCode;
    const mi = indexes.find((i) => i.code === selectedCode);
    return mi ? `${mi.displayName} (${mi.code})` : selectedCode;
  }, [indexes, selectedCode]);

  return (
    <div className="p-3">
      <h2>Indexes</h2>

      <Card className="p-3">
        <h5>Select index</h5>
        <Form.Group className="mb-2">
          <Form.Label>Index</Form.Label>
          <Form.Select
            value={selectedCode}
            onChange={(e) => setSelectedCode(e.target.value)}
            disabled={indexesLoading || !indexes || indexes.length === 0}
            style={{ maxWidth: 420 }}
          >
            {(indexes || []).map((i) => (
              <option key={i.code} value={i.code}>
                {i.displayName} ({i.code})
              </option>
            ))}
          </Form.Select>
        </Form.Group>

        {!!indexesError && (
          <Alert variant="danger">
            Failed to load indexes: {JSON.stringify(indexesError)}
          </Alert>
        )}

        <h5 className="mt-3">{selectedLabel}</h5>
        <IndexMembersTable
          members={members || []}
          isLoading={membersLoading}
          errors={[membersError]}
        />

        {selectedCode === "FAT1000" && (
          <Fattore1000Russell1000CompareTable
            members={members || []}
            membersLoading={membersLoading}
            membersError={membersError}
          />
        )}
      </Card>
    </div>
  );
}

