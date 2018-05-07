package worekleszczy.verticles.dto;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Note {

  final private String _id = UUID.randomUUID().toString();

  @Nonnull
  final private String name;

  @Nonnull
  final private String owner;

  @Nullable
  private String data;

  @Nullable
  private List<String> contributors = new ArrayList<>();

  public Note(String name, String owner, String data) {
    this.name = name;
    this.owner = owner;
    this.data = data;
  }

  public Note(String name, String owner) {
    this(name, owner, null);
  }

  public void addContributor(String user) {
    contributors.add(user);
  }

  @Nonnull
  public String getName() {
    return name;
  }

  @Nullable
  public String getData() {
    return data;
  }

  public void setData(@Nullable String data) {
    this.data = data;
  }

  @Nonnull
  public String getOwner() {
    return owner;
  }

  @Nullable
  public List<String> getContributors() {
    return contributors;
  }

  public void setContributors(@Nullable List<String> contributors) {
    this.contributors = contributors;
  }

  @Nonnull
  public String get_id() {
    return _id;
  }
}
