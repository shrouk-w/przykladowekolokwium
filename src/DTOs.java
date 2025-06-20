public class ProjectDTO
{
    public int ProjectId { get; set; }
    public string Objective { get; set; }
    public DateTime StartDate { get; set; }
    public DateTime? EndDate { get; set; }
    public ArtifactDTO Artifact { get; set; }
    public List<StaffAssignmentDTO> StaffAssignments { get; set; }
}

public class ArtifactDTO
{
    public int ArtifactId { get; set; }
    public string Name { get; set; }
    public DateTime OriginDate { get; set; }
    public int InstitutionId { get; set; }
    public InstitutionDTO Institution { get; set; }
}

public class InstitutionDTO
{
    public int InstitutionId { get; set; }
    public string Name { get; set; }
    public int FoundedYear { get; set; }
}

public class StaffAssignmentDTO
{
    public string FirstName { get; set; }
    public string LastName { get; set; }
    public DateTime HireDate { get; set; }
    public string Role { get; set; }
}

public class NewArtifactProjectDTO
{
    public ArtifactDTO Artifact { get; set; }
    public ProjectDTO Project { get; set; }
}
