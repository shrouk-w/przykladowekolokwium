using System.Data;
using System.Data.SqlClient;
using WebApplication1.Models;

public class Repository
{
    private readonly string _connectionString;

    public Repository(IConfiguration configuration)
    {
        _connectionString = configuration.GetConnectionString("DefaultConnection");
    }

    public async Task<ProjectDTO?> GetProjectByIdAsync(int projectId)
{
    await using var connection = new SqlConnection(_connectionString);
    await connection.OpenAsync();

    var projectQuery = @"
    SELECT
            [p].[ProjectId], [p].[Objective], [p].[StartDate], [p].[EndDate],
                [a].[ArtifactId], [a].[Name] AS [ArtifactName], [a].[OriginDate], [a].[InstitutionId],
                [i].[Name] AS [InstitutionName], [i].[FoundedYear]
    FROM [Projects] AS [p]
    JOIN [Artifacts] AS [a] ON [a].[ArtifactId] = [p].[ArtifactId]
    JOIN [Institutions] AS [i] ON [i].[InstitutionId] = [a].[InstitutionId]
    WHERE [p].[ProjectId] = @projectId";

    await using var command = new SqlCommand(projectQuery, connection);
    command.Parameters.Add("@projectId", SqlDbType.Int).Value = projectId;

    await using var reader = await command.ExecuteReaderAsync();
    if (!await reader.ReadAsync())
    return null;

    var project = new ProjectDTO
    {
        ProjectId = reader.GetInt32(reader.GetOrdinal("ProjectId")),
                Objective = reader.GetString(reader.GetOrdinal("Objective")),
                StartDate = reader.GetDateTime(reader.GetOrdinal("StartDate")),
                EndDate = reader.IsDBNull(reader.GetOrdinal("EndDate")) ? null : reader.GetDateTime(reader.GetOrdinal("EndDate")),
                Artifact = new ArtifactDTO
        {
            ArtifactId = reader.GetInt32(reader.GetOrdinal("ArtifactId")),
                    Name = reader.GetString(reader.GetOrdinal("ArtifactName")),
                    OriginDate = reader.GetDateTime(reader.GetOrdinal("OriginDate")),
                    InstitutionId = reader.GetInt32(reader.GetOrdinal("InstitutionId")),
                    Institution = new InstitutionDTO
            {
                InstitutionId = reader.GetInt32(reader.GetOrdinal("InstitutionId")),
                        Name = reader.GetString(reader.GetOrdinal("InstitutionName")),
                        FoundedYear = reader.GetInt32(reader.GetOrdinal("FoundedYear"))
            }
        },
        StaffAssignments = new List<StaffAssignmentDTO>()
    };

    await reader.CloseAsync();

    var staffQuery = @"
    SELECT
            [s].[FirstName], [s].[LastName], [s].[HireDate], [sa].[Role]
    FROM [Staff_Assignments] AS [sa]
    JOIN [Staff] AS [s] ON [s].[StaffId] = [sa].[StaffId]
    WHERE [sa].[ProjectId] = @projectId";

    await using var staffCmd = new SqlCommand(staffQuery, connection);
    staffCmd.Parameters.Add("@projectId", SqlDbType.Int).Value = projectId;

    await using var staffReader = await staffCmd.ExecuteReaderAsync();
    while (await staffReader.ReadAsync())
    {
        project.StaffAssignments.Add(new StaffAssignmentDTO
        {
            FirstName = staffReader.GetString(0),
                    LastName = staffReader.GetString(1),
                    HireDate = staffReader.GetDateTime(2),
                    Role = staffReader.GetString(3)
        });
    }

    return project;
}

    public async Task AddArtifactAndProjectAsync(NewArtifactProjectDTO request)
{
    if (request == null || request.Artifact == null || request.Project == null)
        throw new ArgumentNullException(nameof(request));

    await using var connection = new SqlConnection(_connectionString);
    await connection.OpenAsync();
    await using var transaction = await connection.BeginTransactionAsync();

    try
    {
        int artifactId;
        int projectId;

        // Insert artifact
        var insertArtifactSql = @"
        INSERT INTO [Artifacts] ([Name], [OriginDate], [InstitutionId])
        OUTPUT INSERTED.[ArtifactId]
        VALUES (@name, @originDate, @institutionId)";

        await using (var insertArtifactCmd = new SqlCommand(insertArtifactSql, connection, (SqlTransaction)transaction))
        {
            insertArtifactCmd.Parameters.Add("@name", SqlDbType.NVarChar, 100).Value = request.Artifact.Name;
            insertArtifactCmd.Parameters.Add("@originDate", SqlDbType.Date).Value = request.Artifact.OriginDate;
            insertArtifactCmd.Parameters.Add("@institutionId", SqlDbType.Int).Value = request.Artifact.InstitutionId;

            artifactId = (int)await insertArtifactCmd.ExecuteScalarAsync();
        }

        // Insert project
        var insertProjectSql = @"
        INSERT INTO [Projects] ([Objective], [StartDate], [EndDate], [ArtifactId])
        OUTPUT INSERTED.[ProjectId]
        VALUES (@objective, @startDate, @endDate, @artifactId)";

        await using (var insertProjectCmd = new SqlCommand(insertProjectSql, connection, (SqlTransaction)transaction))
        {
            insertProjectCmd.Parameters.Add("@objective", SqlDbType.NVarChar, 500).Value = request.Project.Objective;
            insertProjectCmd.Parameters.Add("@startDate", SqlDbType.Date).Value = request.Project.StartDate;
            insertProjectCmd.Parameters.Add("@endDate", SqlDbType.Date).Value =
                    (object?)request.Project.EndDate ?? DBNull.Value;
            insertProjectCmd.Parameters.Add("@artifactId", SqlDbType.Int).Value = artifactId;

            projectId = (int)await insertProjectCmd.ExecuteScalarAsync();
        }

        await transaction.CommitAsync();
    }
    catch
    {
        await transaction.RollbackAsync();
        throw;
    }
}
}
