using System.Data.SqlClient;
using System.Data;
using WebApplication1.Models; // Zamień na odpowiedni namespace z modelami

public class Repository
{
    private readonly string _connectionString;

    public Repository(IConfiguration configuration)
    {
        _connectionString = configuration.GetConnectionString("DefaultConnection");
    }

    public async Task<ProjectDTO?> GetProjectByIdAsync(int projectId)
{
    using var connection = new SqlConnection(_connectionString);
    await connection.OpenAsync();

    var command = new SqlCommand(@"
    SELECT p.ProjectId, p.Objective, p.StartDate, p.EndDate,
        a.ArtifactId, a.Name AS ArtifactName, a.OriginDate, a.InstitutionId,
        i.Name AS InstitutionName, i.FoundedYear
    FROM Projects p
    JOIN Artifacts a ON a.ArtifactId = p.ArtifactId
    JOIN Institutions i ON i.InstitutionId = a.InstitutionId
    WHERE p.ProjectId = @projectId", connection);
    command.Parameters.AddWithValue("@projectId", projectId);

    using var reader = await command.ExecuteReaderAsync();
    if (!reader.Read()) return null;

    var project = new ProjectDTO
    {
        ProjectId = reader.GetInt32(0),
                Objective = reader.GetString(1),
                StartDate = reader.GetDateTime(2),
                EndDate = reader.IsDBNull(3) ? null : reader.GetDateTime(3),
                Artifact = new ArtifactDTO
        {
            Name = reader.GetString(5),
                    OriginDate = reader.GetDateTime(6),
                    Institution = new InstitutionDTO
            {
                InstitutionId = reader.GetInt32(7),
                        Name = reader.GetString(8),
                        FoundedYear = reader.GetInt32(9)
            }
        },
        StaffAssignments = new List<StaffAssignmentDTO>()
    };

    // Staff assignments
    reader.Close();
    var staffCmd = new SqlCommand(@"
    SELECT s.FirstName, s.LastName, s.HireDate, sa.Role
    FROM Staff_Assignments sa
    JOIN Staff s ON s.StaffId = sa.StaffId
    WHERE sa.ProjectId = @projectId", connection);
    staffCmd.Parameters.AddWithValue("@projectId", projectId);

    using var staffReader = await staffCmd.ExecuteReaderAsync();
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
    using var connection = new SqlConnection(_connectionString);
    await connection.OpenAsync();
    using var transaction = connection.BeginTransaction();

    try
    {
        // Insert artifact
        var insertArtifactCmd = new SqlCommand(@"
        INSERT INTO Artifacts (ArtifactId, Name, OriginDate, InstitutionId)
        VALUES (@id, @name, @originDate, @institutionId)", connection, transaction);
        insertArtifactCmd.Parameters.AddWithValue("@id", request.Artifact.ArtifactId);
        insertArtifactCmd.Parameters.AddWithValue("@name", request.Artifact.Name);
        insertArtifactCmd.Parameters.AddWithValue("@originDate", request.Artifact.OriginDate);
        insertArtifactCmd.Parameters.AddWithValue("@institutionId", request.Artifact.InstitutionId);
        await insertArtifactCmd.ExecuteNonQueryAsync();

        // Insert project
        var insertProjectCmd = new SqlCommand(@"
        INSERT INTO Projects (ProjectId, Objective, StartDate, EndDate, ArtifactId)
        VALUES (@id, @objective, @startDate, @endDate, @artifactId)", connection, transaction);
        insertProjectCmd.Parameters.AddWithValue("@id", request.Project.ProjectId);
        insertProjectCmd.Parameters.AddWithValue("@objective", request.Project.Objective);
        insertProjectCmd.Parameters.AddWithValue("@startDate", request.Project.StartDate);
        insertProjectCmd.Parameters.AddWithValue("@endDate", (object?)request.Project.EndDate ?? DBNull.Value);
        insertProjectCmd.Parameters.AddWithValue("@artifactId", request.Artifact.ArtifactId);
        await insertProjectCmd.ExecuteNonQueryAsync();

        await transaction.CommitAsync();
    }
    catch
    {
        await transaction.RollbackAsync();
        throw;
    }
}
}
